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
package com.grookage.fsm.core.services;

import com.grookage.fsm.core.exceptions.FsmException;
import com.grookage.fsm.core.exceptions.FsmException;
import com.grookage.fsm.core.models.entities.Context;
import com.grookage.fsm.core.models.entities.Event;
import com.grookage.fsm.core.models.entities.EventType;
import com.grookage.fsm.core.models.entities.HandlerType;
import com.grookage.fsm.core.models.entities.State;
import com.grookage.fsm.core.models.entities.TransitionKey;
import com.grookage.fsm.core.models.executors.Action;
import com.grookage.fsm.core.models.executors.AfterStateTransitionAction;
import com.grookage.fsm.core.models.executors.BeforeStateTransitionAction;
import com.grookage.fsm.core.models.executors.ErrorAction;
import com.grookage.fsm.core.models.executors.EventAction;
import com.grookage.fsm.core.models.executors.FinalStateAction;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Entity by : koushikr. on 23/10/15.
 */
@Slf4j
@SuppressWarnings({"unchecked", "rawtypes"})
public class ActionService<E extends Event, S extends State, K extends TransitionKey, C extends Context<S, E, K>> {

  private final Map<HandlerType<E, S>, List<Action>> handlers;

  public ActionService() {
    handlers = new HashMap<>();
  }

  public void anyTransition(EventAction<E, S, K, C> action) {
    addHandler(new HandlerType<>(EventType.ANY_STATE_TRANSITION, null, null), action);
  }

  public void beforeTransition(S state, BeforeStateTransitionAction<S, E, K, C> action) {
    addHandler(Objects.isNull(state) ?
            new HandlerType<>(EventType.BEFORE_ANY_TRANSITION, null, null)
            : new HandlerType<>(EventType.BEFORE_STATE_TRANSITION, null, state),
        action
    );
  }

  public void afterTransition(S state, AfterStateTransitionAction<S, E, K, C> action) {
    addHandler(Objects.isNull(state) ?
            new HandlerType<>(EventType.AFTER_ANY_TRANSITION, null, null)
            : new HandlerType<>(EventType.AFTER_STATE_TRANSITION, null, state),
        action
    );
  }

  public void forTransition(E event, S state, EventAction<E, S, K, C> action) {
    addHandler(new HandlerType<>(EventType.STATE_TRANSITION, event, state), action);
  }

  public void onFinalState(S state, FinalStateAction<S, E, K, C> action) {
    addHandler(new HandlerType<>(EventType.FINAL_STATE, null, state), action);
  }

  public void handleTransition(E event, S from, S to, C context) {
    executeActions(new HandlerType<>(EventType.STATE_TRANSITION, null, from), from, to, event, context);
    executeActions(new HandlerType<>(EventType.STATE_TRANSITION, event, from), from, to, event, context);
    executeActions(new HandlerType<>(EventType.ANY_STATE_TRANSITION, null, null), from, to, event, context);
  }

  public void handleLanding(S to, E event, S from, C context) {
    executeActions(new HandlerType<>(EventType.AFTER_STATE_TRANSITION, null, to), from, to, event, context);
    executeActions(new HandlerType<>(EventType.AFTER_ANY_TRANSITION, null, null), from, to, event, context);
  }

  public void handleTakeOff(S from, E event, S to, C context) {
    executeActions(new HandlerType<>(EventType.BEFORE_STATE_TRANSITION, null, from), from, to, event, context);
    executeActions(new HandlerType<>(EventType.BEFORE_ANY_TRANSITION, null, null), from, to, event, context);
  }

  public void handleFinalState(S finalState, C context) {
    List<Action> actions = handlers.get(new HandlerType<>(EventType.FINAL_STATE, null, finalState));
    if (!Objects.isNull(actions)) {
      for (Action action : actions) {
        if (action instanceof FinalStateAction) {
          ((FinalStateAction<S, E, K, C>) action).execute(finalState, context);
        } else if (action instanceof EventAction) {
          ((EventAction<E, S, K, C>) action).call(context);
        }
      }
    }
  }

  public void handleError(FsmException error) {
    List<Action> actions = handlers.get(new HandlerType<>(EventType.ERROR, null, null));
    if (!Objects.isNull(actions)) {
      for (Action action : actions) {
        if (action instanceof ErrorAction) {
          ((ErrorAction<C>) action).call(error, (C) error.getContext());
        } else if (action instanceof EventAction) {
          ((EventAction<E, S, K, C>) action).call((C) error.getContext());
        }
      }
    }
  }

  public void setHandler(EventType eventType, S state, E event, Action action) {
    addHandler(new HandlerType<>(eventType, event, state), action);
  }

  private void addHandler(HandlerType<E, S> handlerType, Action action) {
    handlers.computeIfAbsent(handlerType, k -> new ArrayList<>()).add(action);
  }

  private void executeActions(HandlerType<E, S> handlerType, S from, S to, E event, C context) {
    List<Action> actions = handlers.get(handlerType);
    if (!Objects.isNull(actions)) {
      for (Action action : actions) {
        if (action instanceof BeforeStateTransitionAction && (handlerType.getEventType() == EventType.BEFORE_ANY_TRANSITION || handlerType.getEventType() == EventType.BEFORE_STATE_TRANSITION)) {
          ((BeforeStateTransitionAction<S, E, K, C>) action).execute(from, to, event, context);
        } else if (action instanceof AfterStateTransitionAction && (handlerType.getEventType() == EventType.AFTER_ANY_TRANSITION || handlerType.getEventType() == EventType.AFTER_STATE_TRANSITION)) {
          ((AfterStateTransitionAction<S, E, K, C>) action).execute(from, to, event, context);
        } else if (action instanceof EventAction) {
          ((EventAction<E, S, K, C>) action).call(context);
        }
      }
    }
  }
}
