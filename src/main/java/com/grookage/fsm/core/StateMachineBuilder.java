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

// No new imports needed if not using Pair and keeping fields separate

@NoArgsConstructor
public class StateMachineBuilder<S extends State, E extends Event, K extends TransitionKey, C extends Context<S, E, K>> {

    private MachineBuilderConfig<S, E> machineBuilderConfig;
    private TransitionProcessorHub<S, E, K, C> transitionProcessorHub;
    private ErrorAction<E, S, K, C> errorAction; // Default error action
    private EventAction<E, S, K, C> eventAction; // Default event action (for StateMachine's main transition processing)

    // New fields for action storage
    private EventAction<E, S, K, C> beforeAnyTransitionAction;
    private EventAction<E, S, K, C> afterAnyTransitionAction;

    private S beforeStateTransition_toState;
    private EventAction<E, S, K, C> beforeStateTransition_action;

    private S afterStateTransition_fromState;
    private EventAction<E, S, K, C> afterStateTransition_action;

    private S onFinalStateReached_finalState;
    private EventAction<E, S, K, C> onFinalStateReached_action;

    private S onStateTransition_fromState; // For onStateTransition(S fromState, action)
    private EventAction<E, S, K, C> onStateTransition_action;

    private E onStateTransition_event; // For onStateTransition(E event, S fromState, action)
    private S onStateTransition_event_fromState;
    private EventAction<E, S, K, C> onStateTransition_event_action;
    
    private EventAction<E, S, K, C> anyStateTransitionAction; // For StateMachine.onAnyStateTransition

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
        this.beforeAnyTransitionAction = action;
        return this;
    }

    public StateMachineBuilder<S, E, K, C> withAfterAnyTransitionAction(EventAction<E, S, K, C> action) {
        this.afterAnyTransitionAction = action;
        return this;
    }

    public StateMachineBuilder<S, E, K, C> withBeforeStateTransitionAction(S toState, EventAction<E, S, K, C> action) {
        this.beforeStateTransition_toState = toState;
        this.beforeStateTransition_action = action;
        return this;
    }

    public StateMachineBuilder<S, E, K, C> withAfterStateTransitionAction(S fromState, EventAction<E, S, K, C> action) {
        this.afterStateTransition_fromState = fromState;
        this.afterStateTransition_action = action;
        return this;
    }

    public StateMachineBuilder<S, E, K, C> withOnFinalStateReachedAction(S finalState, EventAction<E, S, K, C> action) {
        this.onFinalStateReached_finalState = finalState;
        this.onFinalStateReached_action = action;
        return this;
    }

    public StateMachineBuilder<S, E, K, C> withAnyStateTransitionAction(EventAction<E, S, K, C> action) {
        this.anyStateTransitionAction = action;
        return this;
    }

    public StateMachineBuilder<S, E, K, C> withOnStateTransitionAction(S fromState, EventAction<E, S, K, C> action) {
        this.onStateTransition_fromState = fromState;
        this.onStateTransition_action = action;
        return this;
    }

    public StateMachineBuilder<S, E, K, C> withOnStateTransitionAction(E event, S fromState, EventAction<E, S, K, C> action) {
        this.onStateTransition_event = event;
        this.onStateTransition_event_fromState = fromState;
        this.onStateTransition_event_action = action;
        return this;
    }

    public StateMachine<S,E,K,C> build(){
        Preconditions.checkNotNull(machineBuilderConfig, "Machine Builder Config can't be null");
        final var startState = machineBuilderConfig.getStartState();
        final var endStates = machineBuilderConfig.getEndStates();

        // Create StateMachine instance with default error and event actions
        this.stateMachine = new StateMachine<>(machineBuilderConfig.getName(),
                startState, transitionProcessorHub, this.errorAction, this.eventAction);

        // Configure basic transitions from config
        final var transitionConfigs = machineBuilderConfig.getTransitionConfigs();
        transitionConfigs.forEach(transitionConfig ->
                stateMachine.onTransition(transitionConfig.getCausedEvent(), transitionConfig.getFrom(), transitionConfig.getTo()));

        // Set end states
        this.stateMachine.end(endStates);

        // Register all collected actions BEFORE start()
        if (beforeAnyTransitionAction != null) {
            this.stateMachine.onBeforeAnyTransition(beforeAnyTransitionAction);
        }
        if (afterAnyTransitionAction != null) {
            this.stateMachine.onAfterAnyTransition(afterAnyTransitionAction);
        }
        if (beforeStateTransition_action != null && beforeStateTransition_toState != null) {
            this.stateMachine.onBeforeStateTransition(beforeStateTransition_toState, beforeStateTransition_action);
        }
        if (afterStateTransition_action != null && afterStateTransition_fromState != null) {
            this.stateMachine.onAfterStateTransition(afterStateTransition_fromState, afterStateTransition_action);
        }
        if (onFinalStateReached_action != null && onFinalStateReached_finalState != null) {
            this.stateMachine.onFinalStateReached(onFinalStateReached_finalState, onFinalStateReached_action);
        }
        if (anyStateTransitionAction != null) {
            this.stateMachine.onAnyStateTransition(anyStateTransitionAction);
        }
        if (onStateTransition_action != null && onStateTransition_fromState != null) {
            this.stateMachine.onStateTransition(onStateTransition_fromState, onStateTransition_action);
        }
        if (onStateTransition_event_action != null && onStateTransition_event != null && onStateTransition_event_fromState != null) {
            this.stateMachine.onStateTransition(onStateTransition_event, onStateTransition_event_fromState, onStateTransition_event_action);
        }
        
        // Start the state machine (which also registers default actions passed to constructor)
        this.stateMachine.start();
        return stateMachine;
    }
}
