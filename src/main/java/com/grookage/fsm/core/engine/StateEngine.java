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
package com.grookage.fsm.core.engine;

import com.grookage.fsm.core.action.DefaultErrorAction;
import com.grookage.fsm.core.exceptions.FsmException;
import com.grookage.fsm.core.exceptions.InvalidStateException;
import com.grookage.fsm.core.exceptions.StateNotFoundException;
import com.grookage.fsm.core.models.entities.Context;
import com.grookage.fsm.core.models.entities.Event;
import com.grookage.fsm.core.models.entities.EventType;
import com.grookage.fsm.core.models.entities.State;
import com.grookage.fsm.core.models.entities.Transition;
import com.grookage.fsm.core.models.entities.TransitionKey;
import com.grookage.fsm.core.models.executors.AfterStateTransitionAction;
import com.grookage.fsm.core.models.executors.BeforeStateTransitionAction;
import com.grookage.fsm.core.models.executors.ErrorAction;
import com.grookage.fsm.core.models.executors.EventAction;
import com.grookage.fsm.core.models.executors.FinalStateAction;
import com.grookage.fsm.core.services.ActionService;
import com.grookage.fsm.core.services.StateManagementService;
import com.grookage.fsm.core.services.TransitionService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Entity by : koushikr. on 23/10/15.
 */
@SuppressWarnings("ALL")
@Slf4j
public class StateEngine<E extends Event, S extends State, K extends TransitionKey, C extends Context<S, E, K>> {

  private final TransitionService<E, S> transitionService;
  private final StateManagementService<S> stateManagementService;
  private final ActionService<E, S, K, C> actionService;

  public StateEngine(final S startState,
      final TransitionService<E, S> transitionService,
      final StateManagementService<S> stateManagementService,
      final ActionService<E, S, K, C> actionService
  ) {
    this.transitionService = transitionService;
    this.stateManagementService = stateManagementService;
    this.actionService = actionService;
    this.stateManagementService.setFrom(startState);
    this.actionService.setHandler(EventType.ERROR, null, null, new DefaultErrorAction<>());
  }

  public void addTransition(final S key, final Transition<E, S> transition) {
    transitionService.addTransition(key, transition);
  }

  public void addEndStates(final Collection<S> endStates) {
    stateManagementService.addEndStates(endStates);
  }

  public StateEngine<E, S, K, C> addErrorAction(final ErrorAction<C> errorHandler) {
    this.actionService.setHandler(EventType.ERROR, null, null, errorHandler);
    return this;
  }

  public StateEngine<E, S, K, C> addBeforeAnyTransitionAction(final BeforeStateTransitionAction<S, E, K, C> before) {
    actionService.beforeTransition(null, before);
    return this;
  }

  public StateEngine<E, S, K, C> addAfterAnyTransitionAction(final AfterStateTransitionAction<S, E, K, C> after) {
    actionService.afterTransition(null, after);
    return this;
  }

  public StateEngine<E, S, K, C> addBeforeStateTransitionAction(final S state,
      final BeforeStateTransitionAction<S, E, K, C> before) {
    actionService.beforeTransition(state, before);
    return this;
  }

  public StateEngine<E, S, K, C> addAfterStateTransitionAction(final S state,
      final AfterStateTransitionAction<S, E, K, C> after) {
    actionService.afterTransition(state, after);
    return this;
  }

  public StateEngine<E, S, K, C> addAnyStateTransitionAction(final EventAction<E, S, K, C> transition) {
    actionService.anyTransition(transition);
    return this;
  }

  public StateEngine<E, S, K, C> addStateTransitionAction(final S state,
      final EventAction<E, S, K, C> action) {
    actionService.forTransition(null, state, action);
    return this;
  }

  public StateEngine<E, S, K, C> addStateTransitionAction(final E event, final S state,
      final EventAction<E, S, K, C> action) {
    actionService.forTransition(event, state, action);
    return this;
  }

  public StateEngine<E, S, K, C> addFinalStateAction(final S state,
      final FinalStateAction<S, E, K, C> action) {
    actionService.onFinalState(state, action);
    return this;
  }

  private void handleStateTransition(final E event, final S from, final S to, final C context) {
    actionService.handleTransition(event, from, to, context);
  }

  private void handleLanding(final S to, final E event, final S from, final C context) {
    actionService.handleLanding(to, event, from, context);
  }

  private void handleTakeOff(final S from, final E event, final S to, final C context) {
    actionService.handleTakeOff(from, event, to, context);
  }

  public Optional<Transition<E, S>> getTransition(final S from, final E event) {
    return transitionService.getTransition(from, event);
  }

  public void handleError(FsmException error) {
    actionService.handleError(error);
  }

  @SneakyThrows
  public void fire(final E event, final C context) {
    final var from = context.getFrom();
    final var transition = getTransition(from, event);
    if (transition.isEmpty()) {
      throw new StateNotFoundException(
          "Invalid Event: " + event + " triggered while in State: " + context.getFrom()
              + " for "
              + context);
    }
    try {
      var to = transition.get().getTo();
      context.setTo(to);
      handleTakeOff(from, event, to, context);
      handleStateTransition(event, from, to, context);
      handleLanding(to, event, from, context);
      stateManagementService.setFrom(to);
      if (stateManagementService.getEndStates().contains(to)) {
        actionService.handleFinalState(to, context);
      }
    } catch (Exception e) {
      handleError(new FsmException(from, event, e, e.getMessage(), context));
      // Rethrow the exception if it's not handled by a custom error handler
      // This maintains the original behavior of failing fast if no error handler is defined.
      if (!(actionService.getHandlers().get(new HandlerType(EventType.ERROR, null, null)).get(0) instanceof DefaultErrorAction)) {
        throw e;
      }
    }
  }

  public void validate() throws InvalidStateException {
    if (Objects.isNull(stateManagementService.getFrom())) {
      throw new InvalidStateException("No start state found");
    }
    if (stateManagementService.getEndStates().isEmpty()) {
      throw new InvalidStateException("No end states found");
    }

    var allStates = stateManagementService.allStates();
    var map = transitionService.getTransitionDetails();
    map.keySet().forEach(state -> {
      allStates.add(state);
      map.get(state).forEach(transition -> {
        allStates.add(transition.getFrom());
        allStates.add(transition.getTo());
      });
    });

    for (S state : allStates) {
      var transitions = (Set<Transition<E, S>>) map.get(state);
      if(isNullOrEmpty(transitions)){
        if(!stateManagementService.getEndStates().contains(state)){
          throw new InvalidStateException("state :"+ state +" is not an end state but"
                  + " has no outgoing transitions");
        }
      } else if(stateManagementService.getEndStates().contains(state)){
        throw new InvalidStateException("state :"+ state +" is an end state"
                + " and cannot have any out going transition");
      }
    }
  }

  private boolean isNullOrEmpty(Collection<?> clx) {
    return Objects.isNull(clx) || clx.isEmpty();
  }
}