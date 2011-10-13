package se.aorwall.logserver.process

import dynamic.DynamicComponent
import org.scalatest.matchers.MustMatchers
import org.mockito.Mockito._
import se.aorwall.logserver.model.process.simple.{SimpleActivityBuilder}
import se.aorwall.logserver.model.{Log, Activity, State}
import se.aorwall.logserver.storage.LogStorage
import akka.util.duration._
import akka.testkit.{CallingThreadDispatcher, TestKit, TestActorRef}
import akka.actor.{Supervisor, ActorRef}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfter, WordSpec}

class ActivityActorSpec extends WordSpec with BeforeAndAfterAll with MustMatchers with TestKit with BeforeAndAfter{

  override protected def afterAll(): scala.Unit = {
    stopTestActor
  }

  "A ActivityActor" must {

    "add incoming log events to request list " in {

      val activityBuilder = mock(classOf[SimpleActivityBuilder])
      val storage = mock(classOf[LogStorage])
      val activityActorRef = TestActorRef(new ActivityActor(activityBuilder, Some(storage), testActor, 0L))
      when(storage.readLogs(activityActorRef.id)).thenReturn(List())
      activityActorRef.start
      val logEvent = new Log("server", "startComponent", "329380921309", "client", 0L, State.START, "hello")

      when(activityBuilder.isFinished()).thenReturn(false)

      activityActorRef ! logEvent
      verify(activityBuilder).addLogEvent(logEvent)
      activityActorRef.stop
    }

    "send the activity to analyser when it's finished " in {
      val activityBuilder = mock(classOf[SimpleActivityBuilder])
      val storage = mock(classOf[LogStorage])
      val activityActorRef = TestActorRef(new ActivityActor(activityBuilder, Some(storage), testActor, 0L))
      when(storage.readLogs(activityActorRef.id)).thenReturn(List())
      activityActorRef.start
      val logEvent = new Log("server", "startComponent", "329380921309", "client", 0L, State.SUCCESS, "hello")
      val activity = new Activity("processId", "correlationId", State.SUCCESS, 0L, 10L)

      when(activityBuilder.isFinished()).thenReturn(true)
      when(activityBuilder.createActivity()).thenReturn(activity)

      activityActorRef ! logEvent
      verify(activityBuilder).addLogEvent(logEvent)

      within (1 seconds) {
        expectMsg(activity) // The activity returned by activityBuilder should be sent to testActor
      }

      activityActorRef.stop
    }

    "send an activity with status TIMEOUT to analyser when timed out" in {
      val timedoutActivity = new Activity("startComponent", "329380921309", State.TIMEOUT, 0L, 0L)

      val process = new DynamicComponent(0L)
      val timeoutStorage = mock(classOf[LogStorage])

      val timeoutActivityActor = TestActorRef(new ActivityActor(process.getActivityBuilder(), Some(timeoutStorage), testActor, 1L))
      timeoutActivityActor.dispatcher = CallingThreadDispatcher.global
      when(timeoutStorage.readLogs(timeoutActivityActor.id)).thenReturn(List())
      timeoutActivityActor.start

      val logEvent = new Log("server", "startComponent", "329380921309", "client", 0L, State.START, "hello")

      timeoutActivityActor ! logEvent

      within (2 seconds) {
        expectMsg(timedoutActivity) // The activity returned by activityBuilder should be sent to testActor
      }

      timeoutActivityActor.stop
    }
  }

}