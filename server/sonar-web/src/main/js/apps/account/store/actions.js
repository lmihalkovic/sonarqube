/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
import { getCurrentUser } from '../../../api/users';

export const REQUEST_USER = 'REQUEST_USER';
export const RECEIVE_USER = 'RECEIVE_USER';
export const ADD_PROJECT_NOTIFICATIONS = 'ADD_PROJECT_NOTIFICATIONS';
export const REMOVE_PROJECT_NOTIFICATIONS = 'REMOVE_PROJECT_NOTIFICATIONS';

export function requestUser () {
  return {
    type: REQUEST_USER
  };
}

export function receiveUser (user) {
  return {
    type: RECEIVE_USER,
    user
  };
}

export function addProjectNotifications (project) {
  return {
    type: ADD_PROJECT_NOTIFICATIONS,
    project
  };
}

export function removeProjectNotifications (project) {
  return {
    type: REMOVE_PROJECT_NOTIFICATIONS,
    project
  };
}

export function fetchUser () {
  return dispatch => {
    dispatch(requestUser());
    return getCurrentUser().then(user => dispatch(receiveUser(user)));
  };
}
