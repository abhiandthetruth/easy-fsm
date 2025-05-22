/*
 * Copyright 2015 Koushik R <rkoushik.14@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.grookage.fsm.core;

import com.grookage.fsm.core.exceptions.InvalidStateException;
import com.grookage.fsm.core.helpers.StateMachineHelper;
import com.google.common.collect.Sets;
import com.grookage.fsm.core.exceptions.FsmException;
import com.grookage.fsm.core.exceptions.InvalidStateException;
import com.grookage.fsm.core.helpers.StateMachineHelper;
import com.grookage.fsm.core.models.executors.AfterStateTransitionAction;
import com.grookage.fsm.core.models.executors.BeforeStateTransitionAction;
import com.grookage.fsm.core.models.executors.ErrorAction;
import com.grookage.fsm.core.models.executors.EventAction;
import com.grookage.fsm.core.models.executors.FinalStateAction;
import com.grookage.fsm.core.stubs.TestContext;
import com.grookage.fsm.core.stubs.TestEvent;
import com.grookage.fsm.core.stubs.TestState;
import com.grookage.fsm.core.stubs.TestTransitionKey;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assert;
import org.junit.Test;

/**
 * Entity by : koushikr. on 26/10/15.
 */
public class StateMachineTest {

  @Test
  public void testForValidStateMachine() throws InvalidStateException {
    final var stateMachineCore = StateMachineHelper.getValidStateMachine();
    stateMachineCore.getStateEngine().validate();
  }

  @Test(expected = InvalidStateException.class)
  public void testForInvalidStateMachine() throws InvalidStateException {
    final var stateMachineCore = StateMachineHelper.getInvalidStateMachine();
    stateMachineCore.getStateEngine().validate();
  }

  @Test
  public void testAnyEvent() {
    final var testContext = new TestContext();
    testContext.setFrom(TestState.STARTED);
    testContext.setTo(TestState.CREATED);
    testContext.setCausedEvent(TestEvent.INITIATE);
    final var stateMachineCore = StateMachineHelper.getValidStateMachine();
    stateMachineCore.getStateEngine().anyTransition(
        context -> Assert.assertSame(TestState.STARTED, context.getFrom()));
    stateMachineCore.getStateEngine().fire(TestEvent.INITIATE, testContext);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidTransitionOnAnyEvent() {
    final var testContext = new TestContext();
    testContext.setFrom(TestState.CREATED);
    testContext.setTo(TestState.CREATED);
    testContext.setCausedEvent(TestEvent.INITIATE);
    final var stateMachineCore = StateMachineHelper.getValidStateMachine();
    stateMachineCore.getStateEngine().anyTransition(
        context -> Assert.assertSame(TestState.STARTED, context.getFrom()));
    stateMachineCore.fire(testContext);
  }

  @Test
  public void testInvalidTransitionOnAnyEventFireGrace() {
    final var testContext = new TestContext();
    testContext.setFrom(TestState.CREATED);
    testContext.setTo(TestState.CREATED);
    testContext.setCausedEvent(TestEvent.INITIATE);
    final var stateMachineCore = StateMachineHelper.getValidStateMachine();
    stateMachineCore.getStateEngine().anyTransition(
        context -> Assert.assertSame(TestState.STARTED, context.getFrom()));
    stateMachineCore.fireGrace(testContext);
  }

  @Test
  public void testForTransition() {
    final var testContext = new TestContext();
    testContext.setFrom(TestState.STARTED);
    testContext.setTo(TestState.CREATED);
    testContext.setCausedEvent(TestEvent.INITIATE);
    final var stateMachineCore = StateMachineHelper.getValidStateMachine();
    stateMachineCore.getStateEngine().anyTransition(
        context -> Assert.assertSame(TestState.STARTED, context.getFrom()));
    stateMachineCore.getStateEngine().fire(TestEvent.INITIATE, testContext);
  }

  @Test
  public void testOnBeforeAnyTransition_ActionExecuted() {
    final var stateMachine = StateMachineHelper.getValidStateMachine();
    final var context = new TestContext();
    context.setFrom(TestState.STARTED);
    context.setCausedEvent(TestEvent.INITIATE);

    final var actionExecuted = new AtomicBoolean(false);
    final var capturedFrom = new AtomicReference<TestState>();
    final var capturedTo = new AtomicReference<TestState>();
    final var capturedEvent = new AtomicReference<TestEvent>();

    stateMachine.onBeforeAnyTransition((from, to, event, ctx) -> {
      actionExecuted.set(true);
      capturedFrom.set(from);
      capturedTo.set(to);
      capturedEvent.set(event);
      Assert.assertSame(context, ctx);
    });

    stateMachine.start(); // Registers default actions
    stateMachine.fire(context);

    Assert.assertTrue("BeforeAnyTransitionAction should be executed", actionExecuted.get());
    Assert.assertEquals(TestState.STARTED, capturedFrom.get());
    Assert.assertEquals(TestState.CREATED, capturedTo.get()); // 'to' is set by StateEngine
    Assert.assertEquals(TestEvent.INITIATE, capturedEvent.get());
  }

  @Test
  public void testOnAfterAnyTransition_ActionExecuted() {
    final var stateMachine = StateMachineHelper.getValidStateMachine();
    final var context = new TestContext();
    context.setFrom(TestState.STARTED);
    context.setCausedEvent(TestEvent.INITIATE);

    final var actionExecuted = new AtomicBoolean(false);
    final var capturedFrom = new AtomicReference<TestState>();
    final var capturedTo = new AtomicReference<TestState>();
    final var capturedEvent = new AtomicReference<TestEvent>();

    stateMachine.onAfterAnyTransition((from, to, event, ctx) -> {
      actionExecuted.set(true);
      capturedFrom.set(from);
      capturedTo.set(to);
      capturedEvent.set(event);
      Assert.assertSame(context, ctx);
    });

    stateMachine.start();
    stateMachine.fire(context);

    Assert.assertTrue("AfterAnyTransitionAction should be executed", actionExecuted.get());
    Assert.assertEquals(TestState.STARTED, capturedFrom.get());
    Assert.assertEquals(TestState.CREATED, capturedTo.get());
    Assert.assertEquals(TestEvent.INITIATE, capturedEvent.get());
  }

  @Test
  public void testOnBeforeStateTransition_ToState_ActionExecuted() {
    final var stateMachine = StateMachineHelper.getValidStateMachine();
    final var context = new TestContext();
    context.setFrom(TestState.STARTED);
    context.setCausedEvent(TestEvent.INITIATE); // This will transition to CREATED

    final var actionExecuted = new AtomicBoolean(false);

    stateMachine.onBeforeStateTransition(TestState.CREATED, (from, to, event, ctx) -> {
      actionExecuted.set(true);
      Assert.assertEquals(TestState.STARTED, from);
      Assert.assertEquals(TestState.CREATED, to);
      Assert.assertEquals(TestEvent.INITIATE, event);
    });

    // Action for a different "to" state, should not be called
    stateMachine.onBeforeStateTransition(TestState.IN_PROGRESS, (from, to, event, ctx) -> {
      Assert.fail("Action for different 'to' state should not be called");
    });

    stateMachine.start();
    stateMachine.fire(context);

    Assert.assertTrue("BeforeStateTransitionAction for CREATED state should be executed", actionExecuted.get());
  }

  @Test
  public void testOnAfterStateTransition_FromState_ActionExecuted() {
    final var stateMachine = StateMachineHelper.getValidStateMachine();
    final var context = new TestContext();
    context.setFrom(TestState.STARTED);
    context.setCausedEvent(TestEvent.INITIATE);

    final var actionExecuted = new AtomicBoolean(false);

    stateMachine.onAfterStateTransition(TestState.STARTED, (from, to, event, ctx) -> {
      actionExecuted.set(true);
      Assert.assertEquals(TestState.STARTED, from);
      Assert.assertEquals(TestState.CREATED, to);
      Assert.assertEquals(TestEvent.INITIATE, event);
    });

    // Action for a different "from" state, should not be called
    stateMachine.onAfterStateTransition(TestState.CREATED, (from, to, event, ctx) -> {
        Assert.fail("Action for different 'from' state should not be called");
    });


    stateMachine.start();
    stateMachine.fire(context);

    Assert.assertTrue("AfterStateTransitionAction for STARTED state should be executed", actionExecuted.get());
  }

  @Test
  public void testOnStateTransition_SpecificEventAndState_ActionExecuted() {
    final var stateMachine = StateMachineHelper.getValidStateMachine();
    final var context = new TestContext();
    context.setFrom(TestState.STARTED);
    context.setCausedEvent(TestEvent.INITIATE);

    final var actionExecuted = new AtomicBoolean(false);

    // This is the specific action we expect to be called
    stateMachine.onStateTransition(TestState.STARTED, TestEvent.INITIATE, ctx -> {
      actionExecuted.set(true);
      Assert.assertEquals(TestState.STARTED, ctx.getFrom());
      Assert.assertEquals(TestState.CREATED, ctx.getTo()); // 'to' should be set by engine
      Assert.assertEquals(TestEvent.INITIATE, ctx.getCausedEvent());
    });

    // Action for different event, should not be called
    stateMachine.onStateTransition(TestState.STARTED, TestEvent.MOVE_TO_PROGRESS, ctx -> {
        Assert.fail("Action for different event should not be called");
    });

    // Action for different state, should not be called
    stateMachine.onStateTransition(TestState.CREATED, TestEvent.INITIATE, ctx -> {
        Assert.fail("Action for different state should not be called");
    });

    stateMachine.start();
    stateMachine.fire(context);

    Assert.assertTrue("Specific EventAction should be executed", actionExecuted.get());
  }

  @Test
  public void testOnAnyStateTransition_AdditionalActionExecuted() {
    final var stateMachine = StateMachineHelper.getValidStateMachine();
    final var context = new TestContext();
    context.setFrom(TestState.STARTED);
    context.setCausedEvent(TestEvent.INITIATE);

    final var defaultActionExecuted = new AtomicBoolean(false);
    final var additionalActionExecuted = new AtomicBoolean(false);

    // Replace default event action for testing
    stateMachine.setEventAction(ctx -> {
      defaultActionExecuted.set(true);
      // Default action still has access to from, to, event through context
       Assert.assertEquals(TestState.STARTED, ctx.getFrom());
       Assert.assertEquals(TestState.CREATED, ctx.getTo());
       Assert.assertEquals(TestEvent.INITIATE, ctx.getCausedEvent());
    });


    stateMachine.onAnyStateTransition(ctx -> {
      additionalActionExecuted.set(true);
      Assert.assertEquals(TestState.STARTED, ctx.getFrom());
      Assert.assertEquals(TestState.CREATED, ctx.getTo());
      Assert.assertEquals(TestEvent.INITIATE, ctx.getCausedEvent());
    });

    stateMachine.start(); // Default eventAction (now replaced) and additional one are registered
    stateMachine.fire(context);

    Assert.assertTrue("Default EventAction (ANY_STATE_TRANSITION) should be executed", defaultActionExecuted.get());
    Assert.assertTrue("Additional EventAction (ANY_STATE_TRANSITION) should be executed", additionalActionExecuted.get());
  }


  @Test
  public void testOnFinalState_ActionExecuted() {
    final var stateMachine = StateMachineHelper.getValidStateMachine(); // Reaches COMPLETED via INITIATE -> MOVE_TO_PROGRESS -> MOVE_TO_COMPLETED
    final var context = new TestContext();
    context.setFrom(TestState.IN_PROGRESS); // Start from IN_PROGRESS
    context.setCausedEvent(TestEvent.MOVE_TO_COMPLETED); // This will transition to COMPLETED

    final var finalStateActionExecuted = new AtomicBoolean(false);

    stateMachine.onFinalState(TestState.COMPLETED, (finalState, ctx) -> {
      finalStateActionExecuted.set(true);
      Assert.assertEquals(TestState.COMPLETED, finalState);
      Assert.assertSame(context, ctx);
      Assert.assertEquals(TestState.IN_PROGRESS, ctx.getFrom()); // from should be the state before final
      Assert.assertEquals(TestState.COMPLETED, ctx.getTo());
    });

    // Action for a different final state, should not be called
     stateMachine.onFinalState(TestState.FAILED, (finalState, ctx) -> {
         Assert.fail("Action for different final state FAILED should not be called");
     });

    stateMachine.start();
    // Manually set the state machine's current internal state to IN_PROGRESS before firing
    // This is because the 'fire' method uses context.getFrom() to find the transition,
    // but the StateEngine's internal current state is also important for some action lookups.
    // For final state, the check happens *after* transition completes and internal state is set to 'to'.
    stateMachine.getStateEngine().getStateManagementService().setFrom(TestState.IN_PROGRESS);
    stateMachine.fire(context);

    Assert.assertTrue("FinalStateAction for COMPLETED state should be executed", finalStateActionExecuted.get());
  }

  @Test
  public void testOnError_AdditionalActionExecuted() {
    final var stateMachine = StateMachineHelper.getValidStateMachine();
    final var context = new TestContext();
    context.setFrom(TestState.STARTED);
    context.setCausedEvent(TestEvent.MOVE_TO_FAILED); // This event will cause a transition

    // This action will throw an exception, triggering the ErrorAction
    stateMachine.onAfterStateTransition(TestState.STARTED, (from, to, event, ctx) -> {
      throw new RuntimeException("Test exception to trigger error action");
    });

    final var defaultErrorActionExecuted = new AtomicBoolean(false);
    final var additionalErrorActionExecuted = new AtomicBoolean(false);
    final var capturedException = new AtomicReference<FsmException>();

    // Replace default error action
    stateMachine.setErrorAction((exception, ctx) -> {
        defaultErrorActionExecuted.set(true);
        Assert.assertNotNull(exception);
        Assert.assertTrue(exception.getMessage().contains("Test exception to trigger error action"));
        Assert.assertSame(context, ctx);
    });

    // Add an additional error action
    stateMachine.onError((exception, ctx) -> {
      additionalErrorActionExecuted.set(true);
      capturedException.set(exception);
      Assert.assertSame(context, ctx);
    });
    
    stateMachine.start(); // Registers default error action (now replaced) and the additional one.
    stateMachine.fire(context);

    Assert.assertTrue("Default ErrorAction should have been executed (or its replacement)", defaultErrorActionExecuted.get());
    Assert.assertTrue("Additional ErrorAction should be executed", additionalErrorActionExecuted.get());
    Assert.assertNotNull(capturedException.get());
    Assert.assertTrue(capturedException.get().getMessage().contains("Test exception to trigger error action"));
    Assert.assertEquals(TestState.STARTED, capturedException.get().getContext().getFrom());
  }

  @Test
  public void testMultipleActions_AllExecuted() {
    final var stateMachine = StateMachineHelper.getValidStateMachine();
    final var context = new TestContext();
    context.setFrom(TestState.STARTED);
    context.setCausedEvent(TestEvent.INITIATE);

    final var action1Executed = new AtomicBoolean(false);
    final var action2Executed = new AtomicBoolean(false);

    stateMachine.onBeforeAnyTransition((from, to, event, ctx) -> action1Executed.set(true));
    stateMachine.onBeforeAnyTransition((from, to, event, ctx) -> action2Executed.set(true));

    stateMachine.start();
    stateMachine.fire(context);

    Assert.assertTrue("First BeforeAnyTransitionAction should be executed", action1Executed.get());
    Assert.assertTrue("Second BeforeAnyTransitionAction should be executed", action2Executed.get());
  }

}
