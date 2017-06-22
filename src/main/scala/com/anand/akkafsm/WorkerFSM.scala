package com.anand.akkafsm

import akka.actor._
import akka.util.Timeout
import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global


sealed trait WorkerState
case object Active extends WorkerState
case object Idle extends WorkerState
case object WaitingForCompletion extends WorkerState

sealed trait Message
case object RequestWork extends Message
case object WorkAvailable extends Message
case object Completed extends Message
case object Failed extends Message
case object ProcessMessages extends Message
case class Data(msg: String) extends Message
case object Uninitialized extends Message

case class Job(input: String)

case class StateMachineData(msgQueue: mutable.Queue[Job])


/**
 * The Worker Actor implements the FiniteStateMachine interface from Akka Actors.
 * The FSM takes [WorkerState, StateMachineData] as a parameter to handle various states. The StateMachineData is
 * a mutable.Queue which queues up vistoscheduler.Job(s) for execution and executes one Job at a time.
 */
class WorkerFSM extends Stash with FSM[WorkerState, StateMachineData] with ActorLogging {
  implicit val timeout = Timeout(5.minute)

  val router: ActorRef = RouterService(context.system).instance

  startWith(Idle, StateMachineData(msgQueue = mutable.Queue[Job]()))

  onTransition {

    case Idle -> Active => log.debug("state transitioning from Idle to Active..." + stateData)
    case Active -> Idle => log.debug("state transitioning from Active to Idle..." + stateData)
    case WaitingForCompletion -> Active => log.debug("state changing from WaitingForCompletion to Active..." + stateData)
    case Active -> WaitingForCompletion =>
      log.debug("state changing from Active to WaitingForCompletion..." + stateData)
      unstashAll()

  }

  onTermination {
    case StopEvent(FSM.Shutdown, state, d @ StateMachineData(msgQueue)) => {
      if(msgQueue.nonEmpty) {
        log.info("Worker shutdown. Sending queued messages back to router...")
        log.info("msgQueue = " + msgQueue)
        msgQueue.foreach(msg => router ! msg)
      }
    }
  }

  /**
   * The following section handles the Idle state of a Worker Actor. It either requests the Router for more work or
   * changes the state of the Worker to Active and starts processing.
   */
  when(Idle) {
    case Event(WorkAvailable, _) => {
      router ! RequestWork
      stay()
    }
    case Event(job: Job, d @ StateMachineData(msgQueue)) => {
      goto(Active) using d.copy(msgQueue += job)
    }
    case Event(ProcessMessages, d @ StateMachineData(msgQueue)) if msgQueue.nonEmpty => {
      self ! ProcessMessages
      goto(Active)
    }
  }

  /**
   * The following section handles the Active state of a Worker Actor. It either queues up Jobs or submits a Job for
   * execution. When a Job is submitted for execution, the state is changed to WaitingForCompletion.
   */
  when(Active) {
    case Event(job: Job, d @ StateMachineData(msgQueue)) => {
      stash()
      self ! ProcessMessages
      stay()
    }
    case Event(ProcessMessages, d @ StateMachineData(msgQueue)) if msgQueue.nonEmpty => {
      val job = msgQueue.dequeue()
      processJob(job).map {
        unit =>
          self ! Completed
      }
      goto(WaitingForCompletion) using d
    }
    case Event(WorkAvailable, _) => {
      log.debug("Worker is busy...")
      stay()
    }

  }

  /**
   * The following section of code handles the WaitingForCompletion state of a Worker Actor. If a Job is complete,
   * it will prompt further processing of Jobs (if available). If a new Job is received for processing before the
   * current Job is completed, it will be queued up.
   */
  when(WaitingForCompletion) {
    case Event(Completed, d @ StateMachineData(msgQueue)) if msgQueue.nonEmpty => {
      self ! ProcessMessages
      goto(Idle)
    }
    case Event(Completed, d @ StateMachineData(msgQueue)) if !msgQueue.nonEmpty => {
      goto(Idle)
    }
    case Event(job: Job, d @ StateMachineData(msgQueue)) => {
      stay() using d.copy(msgQueue += job)
    }
    case Event(ProcessMessages, _) => {
      log.debug(s"Worker ${self.path.name} is busy...")
      stay()
    }
    case Event(WorkAvailable, _) => {
      log.debug("Worker is busy...")
      stay()
    }
  }

  def processJob(msg: Job): Future[Unit] = Future {
    //log.info(s"Processing msg $msg ...")
    Thread.sleep(2000)
    log.info(s"Completed processing of ${msg.input}")
  }

}

object RouterService extends SystemScoped {
  override lazy val instanceProps = Props[RouterActor]
  override lazy val instanceName = "router-service"
}

trait SystemScoped extends ExtensionId[SystemScopedImpl] with ExtensionIdProvider {

  final override def lookup = this
  final override def createExtension(system: ExtendedActorSystem) = new SystemScopedImpl(system, instanceProps, instanceName)

  protected def instanceProps: Props
  protected def instanceName: String
}

class SystemScopedImpl(system: ActorSystem, props: Props, name: String) extends Extension {
  val instance: ActorRef = system.actorOf(props, name = name)
}