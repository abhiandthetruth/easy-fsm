/*
 * Copyright 2022 Koushik R <rkoushik.14@gmail.com>.
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.grookage.fsm.core.config.MachineBuilderConfig;
import com.grookage.fsm.core.exceptions.InvalidStateException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Sets;
import com.grookage.fsm.core.config.MachineBuilderConfig;
import com.grookage.fsm.core.config.TransitionConfig;
import com.grookage.fsm.core.exceptions.InvalidStateException;
import com.grookage.fsm.core.helpers.ResourceHelper;
import com.grookage.fsm.core.models.entities.Context; // For ContextSnapshot
import com.grookage.fsm.core.stubs.TestContext;
import com.grookage.fsm.core.stubs.TestEvent;
import com.grookage.fsm.core.stubs.TestHub;
import com.grookage.fsm.core.stubs.TestState;
import com.grookage.fsm.core.stubs.TestTransitionKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class StateMachineBuilderTest {

    // Using the same ContextSnapshot as in StateMachineTest
    static record ContextSnapshot(TestState from, TestState to, TestEvent event, String transitionKeyTag) {
        ContextSnapshot(Context<TestState, TestEvent, ?> context) {
            this(context.getFrom(), context.getTo(), context.getCausedEvent(),
                context.getTransitionKey() != null ? context.getTransitionKey().toString() : null);
        }
    }

    private MachineBuilderConfig<TestState, TestEvent> basicMachineConfig;
    private TransitionProcessorHub<TestState, TestEvent, TestTransitionKey, TestContext> testHub;

    @Before
    public void setUp() {
        basicMachineConfig = new MachineBuilderConfig<>();
        basicMachineConfig.setName("TestFSM_Builder");
        basicMachineConfig.setStartState(TestState.STARTED);
        basicMachineConfig.setEndStates(Sets.newHashSet(TestState.COMPLETED, TestState.FAILED));
        basicMachineConfig.setTransitionConfigs(List.of(
                new TransitionConfig<>(TestEvent.INITIATE, TestState.STARTED, TestState.CREATED),
                new TransitionConfig<>(TestEvent.MOVE_TO_PROGRESS, TestState.CREATED, TestState.IN_PROGRESS),
                new TransitionConfig<>(TestEvent.MOVE_TO_COMPLETED, TestState.IN_PROGRESS, TestState.COMPLETED),
                new TransitionConfig<>(TestEvent.MOVE_TO_FAILED, TestState.IN_PROGRESS, TestState.FAILED)
        ));
        testHub = TestHub.builder().build();
    }

    @Test
    public void testValidStateMachineBuilder() throws Exception {
        final var machineBuilderConfig = ResourceHelper.getResource("stateMachine.json", new TypeReference<MachineBuilderConfig<TestState, TestEvent>>() {
        });
        Assert.assertNotNull(machineBuilderConfig);
        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(machineBuilderConfig)
                .withTransitionProcessorHub(testHub)
                .build();
        Assert.assertNotNull(stateMachine);
    }

    @Test(expected = InvalidStateException.class)
    public void testForInvalidStateMachine() throws Exception {
        final var machineBuilderConfig = ResourceHelper.getResource("invalidMachine.json", new TypeReference<MachineBuilderConfig<TestState, TestEvent>>() {
        });
        new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(machineBuilderConfig)
                .withTransitionProcessorHub(testHub)
                .build();
    }

    @Test
    public void testWithBeforeAnyTransitionAction_Builder() {
        final var actionExecuted = new AtomicBoolean(false);
        final List<ContextSnapshot> capturedContexts = new ArrayList<>();

        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(basicMachineConfig)
                .withTransitionProcessorHub(testHub)
                .withBeforeAnyTransitionAction(ctx -> {
                    actionExecuted.set(true);
                    capturedContexts.add(new ContextSnapshot(ctx));
                })
                .build();

        TestContext context = new TestContext();
        context.setFrom(TestState.STARTED);
        context.setCausedEvent(TestEvent.INITIATE);
        stateMachine.fire(context);

        Assert.assertTrue(actionExecuted.get());
        Assert.assertEquals(1, capturedContexts.size());
        ContextSnapshot snapshot = capturedContexts.get(0);
        Assert.assertEquals(TestState.STARTED, snapshot.from());
        Assert.assertEquals(TestState.CREATED, snapshot.to()); // 'to' is set by StateMachine.fire
        Assert.assertEquals(TestEvent.INITIATE, snapshot.event());
    }

    @Test
    public void testWithAfterAnyTransitionAction_Builder() {
        final var actionExecuted = new AtomicBoolean(false);
        final List<ContextSnapshot> capturedContexts = new ArrayList<>();

        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(basicMachineConfig)
                .withTransitionProcessorHub(testHub)
                .withAfterAnyTransitionAction(ctx -> {
                    actionExecuted.set(true);
                    capturedContexts.add(new ContextSnapshot(ctx));
                })
                .build();

        TestContext context = new TestContext();
        context.setFrom(TestState.STARTED);
        context.setCausedEvent(TestEvent.INITIATE);
        stateMachine.fire(context);

        Assert.assertTrue(actionExecuted.get());
        Assert.assertEquals(1, capturedContexts.size());
        ContextSnapshot snapshot = capturedContexts.get(0);
        Assert.assertEquals(TestState.STARTED, snapshot.from());
        Assert.assertEquals(TestState.CREATED, snapshot.to());
        Assert.assertEquals(TestEvent.INITIATE, snapshot.event());
    }

    @Test
    public void testWithBeforeStateTransitionAction_Builder() {
        final var correctActionExecuted = new AtomicBoolean(false);
        final var wrongActionExecuted = new AtomicBoolean(false);

        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(basicMachineConfig)
                .withTransitionProcessorHub(testHub)
                .withBeforeStateTransitionAction(TestState.CREATED, ctx -> {
                    Assert.assertEquals(TestState.CREATED, ctx.getTo());
                    correctActionExecuted.set(true);
                })
                .withBeforeStateTransitionAction(TestState.IN_PROGRESS, ctx -> wrongActionExecuted.set(true))
                .build();

        TestContext context = new TestContext();
        context.setFrom(TestState.STARTED);
        context.setCausedEvent(TestEvent.INITIATE); // Transitions to CREATED
        stateMachine.fire(context);

        Assert.assertTrue(correctActionExecuted.get());
        Assert.assertFalse(wrongActionExecuted.get());
    }

    @Test
    public void testWithAfterStateTransitionAction_Builder() {
        final var correctActionExecuted = new AtomicBoolean(false);
        final var wrongActionExecuted = new AtomicBoolean(false);

        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(basicMachineConfig)
                .withTransitionProcessorHub(testHub)
                .withAfterStateTransitionAction(TestState.STARTED, ctx -> {
                    Assert.assertEquals(TestState.STARTED, ctx.getFrom());
                    correctActionExecuted.set(true);
                })
                .withAfterStateTransitionAction(TestState.CREATED, ctx -> wrongActionExecuted.set(true))
                .build();

        TestContext context = new TestContext();
        context.setFrom(TestState.STARTED);
        context.setCausedEvent(TestEvent.INITIATE);
        stateMachine.fire(context);

        Assert.assertTrue(correctActionExecuted.get());
        Assert.assertFalse(wrongActionExecuted.get());
    }

    @Test
    public void testWithFinalStateAction_Builder() {
        final var correctActionExecuted = new AtomicBoolean(false);
        final var wrongActionExecuted = new AtomicBoolean(false);

        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(basicMachineConfig)
                .withTransitionProcessorHub(testHub)
                .withFinalStateAction(TestState.COMPLETED, ctx -> {
                    Assert.assertEquals(TestState.COMPLETED, ctx.getTo());
                    correctActionExecuted.set(true);
                })
                .withFinalStateAction(TestState.FAILED, ctx -> wrongActionExecuted.set(true))
                .build();

        // Sequence to reach IN_PROGRESS state
        TestContext ctx1 = new TestContext();
        ctx1.setFrom(TestState.STARTED);
        ctx1.setCausedEvent(TestEvent.INITIATE);
        stateMachine.fire(ctx1); // To CREATED

        TestContext ctx2 = new TestContext();
        ctx2.setFrom(TestState.CREATED);
        ctx2.setCausedEvent(TestEvent.MOVE_TO_PROGRESS);
        stateMachine.fire(ctx2); // To IN_PROGRESS

        // Transition to final state COMPLETED
        TestContext finalContext = new TestContext();
        finalContext.setFrom(TestState.IN_PROGRESS);
        finalContext.setCausedEvent(TestEvent.MOVE_TO_COMPLETED);
        stateMachine.fire(finalContext);

        Assert.assertTrue(correctActionExecuted.get());
        Assert.assertFalse(wrongActionExecuted.get());
    }

     @Test
    public void testMultipleActionsRegisteredViaBuilder_AllExecuted() {
        final AtomicBoolean beforeAny1 = new AtomicBoolean(false);
        final AtomicBoolean beforeAny2 = new AtomicBoolean(false);
        final AtomicBoolean afterFromStarted1 = new AtomicBoolean(false);
        final AtomicBoolean afterFromStarted2 = new AtomicBoolean(false);


        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(basicMachineConfig)
                .withTransitionProcessorHub(testHub)
                .withBeforeAnyTransitionAction(ctx -> beforeAny1.set(true))
                .withBeforeAnyTransitionAction(ctx -> beforeAny2.set(true))
                .withAfterStateTransitionAction(TestState.STARTED, ctx -> afterFromStarted1.set(true))
                .withAfterStateTransitionAction(TestState.STARTED, ctx -> afterFromStarted2.set(true))
                .build();

        TestContext context = new TestContext();
        context.setFrom(TestState.STARTED);
        context.setCausedEvent(TestEvent.INITIATE);
        stateMachine.fire(context);

        Assert.assertTrue("First beforeAny action should execute", beforeAny1.get());
        Assert.assertTrue("Second beforeAny action should execute", beforeAny2.get());
        Assert.assertTrue("First afterFromStarted action should execute", afterFromStarted1.get());
        Assert.assertTrue("Second afterFromStarted action should execute", afterFromStarted2.get());
    }
}
