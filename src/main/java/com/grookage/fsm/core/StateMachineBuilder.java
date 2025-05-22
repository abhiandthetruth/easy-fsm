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

import com.google.common.base.Preconditions;
import com.grookage.fsm.core.config.MachineBuilderConfig;
import com.grookage.fsm.core.hubs.TransitionProcessorHub;
import com.grookage.fsm.core.models.entities.Context;
import com.grookage.fsm.core.models.entities.Event;
import com.grookage.fsm.core.models.entities.State;
import com.grookage.fsm.core.models.entities.TransitionKey;
import com.google.common.base.Preconditions;
import com.grookage.fsm.core.config.MachineBuilderConfig;
import com.grookage.fsm.core.hubs.TransitionProcessorHub;
import com.grookage.fsm.core.models.entities.Context;
import com.grookage.fsm.core.models.entities.Event;
import com.grookage.fsm.core.models.entities.State;
import com.grookage.fsm.core.models.entities.TransitionKey;
import com.grookage.fsm.core.models.executors.ErrorAction;
import com.grookage.fsm.core.models.executors.EventAction;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
public class StateMachineBuilder<S extends State, E extends Event, K extends TransitionKey, C extends Context<S, E, K>> {

    // Helper class
    private static class ActionWithState<S_TYPE, ACTION_TYPE> {
        S_TYPE state;
        ACTION_TYPE action;
        ActionWithState(S_TYPE state, ACTION_TYPE action) {
            this.state = state;
            this.action = action;
        }
        S_TYPE getState() { return state; }
        ACTION_TYPE getAction() { return action; }
    }

    private MachineBuilderConfig<S, E> machineBuilderConfig;
    private TransitionProcessorHub<S, E, K, C> transitionProcessorHub;
    private ErrorAction<E, S, K, C> errorAction;
    private EventAction<E, S, K, C> eventAction; // This is the main/default event action

    // New fields for action storage
    private List<EventAction<E, S, K, C>> beforeAnyTransitionActions = new ArrayList<>();
    private List<EventAction<E, S, K, C>> afterAnyTransitionActions = new ArrayList<>();
    private List<ActionWithState<S, EventAction<E, S, K, C>>> beforeStateTransitionActionsList = new ArrayList<>();
    private List<ActionWithState<S, EventAction<E, S, K, C>>> afterStateTransitionActionsList = new ArrayList<>();
    private List<ActionWithState<S, EventAction<E, S, K, C>>> finalStateActionsList = new ArrayList<>();

    @Getter
    private StateMachine<S,E,K,C> stateMachine;

    public StateMachineBuilder<S, E, K, C> withMachineBuilderConfig(MachineBuilderConfig<S, E> machineBuilderConfig){
        this.machineBuilderConfig = machineBuilderConfig;
        return this;
    }

    public StateMachineBuilder<S, E, K, C> withTransitionProcessorHub(TransitionProcessorHub<S, E, K, C> transitionProcessorHub){
        this.transitionProcessorHub = transitionProcessorHub;
        return this;
    }

    public StateMachineBuilder<S, E, K, C> withEventAction(EventAction<E, S, K, C> eventAction){
        this.eventAction = eventAction;
        return this;
    }

    public StateMachineBuilder<S, E, K, C> withErrorAction(ErrorAction<E, S, K, C> errorAction) {
        this.errorAction = errorAction;
        return this;
    }

    // New public registration methods
    public StateMachineBuilder<S, E, K, C> withBeforeAnyTransitionAction(EventAction<E, S, K, C> action) {
        this.beforeAnyTransitionActions.add(action);
        return this;
    }

    public StateMachineBuilder<S, E, K, C> withAfterAnyTransitionAction(EventAction<E, S, K, C> action) {
        this.afterAnyTransitionActions.add(action);
        return this;
    }

    public StateMachineBuilder<S, E, K, C> withBeforeStateTransitionAction(S toState, EventAction<E, S, K, C> action) {
        this.beforeStateTransitionActionsList.add(new ActionWithState<>(toState, action));
        return this;
    }

    public StateMachineBuilder<S, E, K, C> withAfterStateTransitionAction(S fromState, EventAction<E, S, K, C> action) {
        this.afterStateTransitionActionsList.add(new ActionWithState<>(fromState, action));
        return this;
    }

    public StateMachineBuilder<S, E, K, C> withFinalStateAction(S finalState, EventAction<E, S, K, C> action) {
        this.finalStateActionsList.add(new ActionWithState<>(finalState, action));
        return this;
    }

    public StateMachine<S,E,K,C> build(){
        Preconditions.checkNotNull(machineBuilderConfig, "Machine Builder Config can't be null");
        final var startState = machineBuilderConfig.getStartState();
        final var endStates = machineBuilderConfig.getEndStates();
        this.stateMachine = new StateMachine<>(machineBuilderConfig.getName(),
                startState, transitionProcessorHub, errorAction, eventAction);

        // Configure basic transitions from config
        final var transitionConfigs = machineBuilderConfig.getTransitionConfigs();
        transitionConfigs.forEach(transitionConfig ->
                stateMachine.onTransition(transitionConfig.getCausedEvent(), transitionConfig.getFrom(), transitionConfig.getTo()));

        // Set end states
        this.stateMachine.end(endStates);

        // Register all collected actions BEFORE start()
        for (EventAction<E, S, K, C> action : beforeAnyTransitionActions) {
            this.stateMachine.onBeforeAnyTransition(action);
        }
        for (EventAction<E, S, K, C> action : afterAnyTransitionActions) {
            this.stateMachine.onAfterAnyTransition(action);
        }
        for (ActionWithState<S, EventAction<E, S, K, C>> item : beforeStateTransitionActionsList) {
            this.stateMachine.onBeforeStateTransition(item.getState(), item.getAction());
        }
        for (ActionWithState<S, EventAction<E, S, K, C>> item : afterStateTransitionActionsList) {
            this.stateMachine.onAfterStateTransition(item.getState(), item.getAction());
        }
        for (ActionWithState<S, EventAction<E, S, K, C>> item : finalStateActionsList) {
            this.stateMachine.onFinalState(item.getState(), item.getAction());
        }

        // Start the state machine
        this.stateMachine.start();
        return stateMachine;
    }
}
