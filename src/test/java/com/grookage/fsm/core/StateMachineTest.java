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
import com.grookage.fsm.core.models.entities.Context; // Added for ContextSnapshot
import com.grookage.fsm.core.stubs.TestContext;
import com.grookage.fsm.core.stubs.TestEvent;
import com.grookage.fsm.core.stubs.TestState;
import java.util.ArrayList; // Added for List
import java.util.List;    // Added for List
import java.util.concurrent.atomic.AtomicBoolean; // Added for AtomicBoolean
import org.junit.Assert;
import org.junit.Test;

/**
 * Entity by : koushikr. on 26/10/15.
 */
public class StateMachineTest {

  // ContextSnapshot record for capturing context details
  // Adding transitionKeyTag to verify it's populated correctly if available.
  static record ContextSnapshot(TestState from, TestState to, TestEvent event, String transitionKeyTag) {
    ContextSnapshot(Context<TestState, TestEvent, ?> context) {
      this(context.getFrom(), context.getTo(), context.getCausedEvent(),
          // Assuming getTransitionKey() might return null or its string representation
          context.getTransitionKey() != null ? context.getTransitionKey().toString() : null);
    }
  }

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
    final List<ContextSnapshot> capturedContexts = new ArrayList<>();

    stateMachine.onBeforeAnyTransition(ctx -> {
      actionExecuted.set(true);
      capturedContexts.add(new ContextSnapshot(ctx));
    });
    stateMachine.start();
    stateMachine.fire(context);

    Assert.assertTrue("onBeforeAnyTransition action should be executed", actionExecuted.get());
    Assert.assertEquals(1, capturedContexts.size());
    ContextSnapshot snapshot = capturedContexts.get(0);
    Assert.assertEquals(TestState.STARTED, snapshot.from());
    // In 'before' actions, 'to' state is set by StateMachine.fire logic before calling these actions
    Assert.assertEquals(TestState.CREATED, snapshot.to());
    Assert.assertEquals(TestEvent.INITIATE, snapshot.event());
  }

  @Test
  public void testMultipleOnBeforeAnyTransition_AllExecuted() {
    final var stateMachine = StateMachineHelper.getValidStateMachine();
    final var context = new TestContext();
    context.setFrom(TestState.STARTED);
    context.setCausedEvent(TestEvent.INITIATE);

    final var action1Executed = new AtomicBoolean(false);
    final var action2Executed = new AtomicBoolean(false);

    stateMachine.onBeforeAnyTransition(ctx -> action1Executed.set(true));
    stateMachine.onBeforeAnyTransition(ctx -> action2Executed.set(true));
    stateMachine.start();
    stateMachine.fire(context);

    Assert.assertTrue("First onBeforeAnyTransition action should be executed", action1Executed.get());
    Assert.assertTrue("Second onBeforeAnyTransition action should be executed", action2Executed.get());
  }


  @Test
  public void testOnAfterAnyTransition_ActionExecuted() {
    final var stateMachine = StateMachineHelper.getValidStateMachine();
    final var context = new TestContext();
    context.setFrom(TestState.STARTED);
    context.setCausedEvent(TestEvent.INITIATE);

    final var actionExecuted = new AtomicBoolean(false);
    final List<ContextSnapshot> capturedContexts = new ArrayList<>();

    stateMachine.onAfterAnyTransition(ctx -> {
      actionExecuted.set(true);
      capturedContexts.add(new ContextSnapshot(ctx));
    });
    stateMachine.start();
    stateMachine.fire(context);

    Assert.assertTrue("onAfterAnyTransition action should be executed", actionExecuted.get());
    Assert.assertEquals(1, capturedContexts.size());
    ContextSnapshot snapshot = capturedContexts.get(0);
    Assert.assertEquals(TestState.STARTED, snapshot.from());
    Assert.assertEquals(TestState.CREATED, snapshot.to());
    Assert.assertEquals(TestEvent.INITIATE, snapshot.event());
  }

  @Test
  public void testOnBeforeStateTransition_ToState_ActionExecuted() {
    final var stateMachine = StateMachineHelper.getValidStateMachine();
    final var context = new TestContext();
    context.setFrom(TestState.STARTED);
    context.setCausedEvent(TestEvent.INITIATE); // Transitions to CREATED

    final var correctActionExecuted = new AtomicBoolean(false);
    final var wrongActionExecuted = new AtomicBoolean(false);
    final List<ContextSnapshot> capturedContexts = new ArrayList<>();

    stateMachine.onBeforeStateTransition(TestState.CREATED, ctx -> {
      correctActionExecuted.set(true);
      capturedContexts.add(new ContextSnapshot(ctx));
    });
    stateMachine.onBeforeStateTransition(TestState.IN_PROGRESS, ctx -> wrongActionExecuted.set(true));
    stateMachine.start();
    stateMachine.fire(context);

    Assert.assertTrue("onBeforeStateTransition for CREATED state should be executed", correctActionExecuted.get());
    Assert.assertFalse("onBeforeStateTransition for IN_PROGRESS state should NOT be executed", wrongActionExecuted.get());
    Assert.assertEquals(1, capturedContexts.size());
    ContextSnapshot snapshot = capturedContexts.get(0);
    Assert.assertEquals(TestState.STARTED, snapshot.from());
    Assert.assertEquals(TestState.CREATED, snapshot.to());
    Assert.assertEquals(TestEvent.INITIATE, snapshot.event());
  }

  @Test
  public void testOnAfterStateTransition_FromState_ActionExecuted() {
    final var stateMachine = StateMachineHelper.getValidStateMachine();
    final var context = new TestContext();
    context.setFrom(TestState.STARTED);
    context.setCausedEvent(TestEvent.INITIATE);

    final var correctActionExecuted = new AtomicBoolean(false);
    final var wrongActionExecuted = new AtomicBoolean(false);
    final List<ContextSnapshot> capturedContexts = new ArrayList<>();

    stateMachine.onAfterStateTransition(TestState.STARTED, ctx -> {
      correctActionExecuted.set(true);
      capturedContexts.add(new ContextSnapshot(ctx));
    });
    stateMachine.onAfterStateTransition(TestState.CREATED, ctx -> wrongActionExecuted.set(true));
    stateMachine.start();
    stateMachine.fire(context);

    Assert.assertTrue("onAfterStateTransition for STARTED state should be executed", correctActionExecuted.get());
    Assert.assertFalse("onAfterStateTransition for CREATED state should NOT be executed", wrongActionExecuted.get());
    Assert.assertEquals(1, capturedContexts.size());
    ContextSnapshot snapshot = capturedContexts.get(0);
    Assert.assertEquals(TestState.STARTED, snapshot.from());
    Assert.assertEquals(TestState.CREATED, snapshot.to());
    Assert.assertEquals(TestEvent.INITIATE, snapshot.event());
  }

  @Test
  public void testOnFinalState_ActionExecuted() {
    final var stateMachine = StateMachineHelper.getValidStateMachine(); // Ends at COMPLETED or FAILED
    final var context = new TestContext();
    // context.setFrom(TestState.IN_PROGRESS); // Set later after sequence
    context.setCausedEvent(TestEvent.MOVE_TO_COMPLETED); // Reaches final state COMPLETED

    final var correctFinalActionExecuted = new AtomicBoolean(false);
    final var wrongFinalActionExecuted = new AtomicBoolean(false);
    final List<ContextSnapshot> capturedContexts = new ArrayList<>();

    stateMachine.onFinalState(TestState.COMPLETED, ctx -> {
      correctFinalActionExecuted.set(true);
      capturedContexts.add(new ContextSnapshot(ctx));
    });
    stateMachine.onFinalState(TestState.FAILED, ctx -> wrongFinalActionExecuted.set(true));
    stateMachine.start();

    // Simulate sequence to get to IN_PROGRESS first.
    TestContext setupContext1 = new TestContext();
    setupContext1.setFrom(TestState.STARTED);
    setupContext1.setCausedEvent(TestEvent.INITIATE);
    stateMachine.fire(setupContext1); // Now in CREATED

    TestContext setupContext2 = new TestContext();
    setupContext2.setFrom(TestState.CREATED);
    setupContext2.setCausedEvent(TestEvent.MOVE_TO_PROGRESS);
    stateMachine.fire(setupContext2); // Now in IN_PROGRESS

    // Now set the 'from' for the context leading to final state
    context.setFrom(TestState.IN_PROGRESS);
    stateMachine.fire(context);

    Assert.assertTrue("onFinalState for COMPLETED state should be executed", correctFinalActionExecuted.get());
    Assert.assertFalse("onFinalState for FAILED state should NOT be executed", wrongFinalActionExecuted.get());
    Assert.assertEquals(1, capturedContexts.size());
    ContextSnapshot snapshot = capturedContexts.get(0);
    Assert.assertEquals(TestState.IN_PROGRESS, snapshot.from());
    Assert.assertEquals(TestState.COMPLETED, snapshot.to());
    Assert.assertEquals(TestEvent.MOVE_TO_COMPLETED, snapshot.event());
  }

  @Test
  public void testOnFinalState_FireGrace() {
    final var stateMachine = StateMachineHelper.getValidStateMachine();
    final var context = new TestContext();
    // context.setFrom(TestState.IN_PROGRESS); // Set later after sequence
    context.setCausedEvent(TestEvent.MOVE_TO_COMPLETED);

    final var finalActionExecuted = new AtomicBoolean(false);
    stateMachine.onFinalState(TestState.COMPLETED, ctx -> finalActionExecuted.set(true));
    stateMachine.start();

    // Simulate sequence to get to IN_PROGRESS
    TestContext ctx1 = new TestContext();
    ctx1.setFrom(TestState.STARTED);
    ctx1.setCausedEvent(TestEvent.INITIATE);
    stateMachine.fireGrace(ctx1);

    TestContext ctx2 = new TestContext();
    ctx2.setFrom(TestState.CREATED);
    ctx2.setCausedEvent(TestEvent.MOVE_TO_PROGRESS);
    stateMachine.fireGrace(ctx2);
    
    context.setFrom(TestState.IN_PROGRESS);
    stateMachine.fireGrace(context);
    Assert.assertTrue("onFinalState action should be executed with fireGrace", finalActionExecuted.get());
  }
}
