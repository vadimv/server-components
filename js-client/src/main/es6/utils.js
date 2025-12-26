/*
 *  Copyright 2015 Aleksey Fomkin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

export function getDeviceId() {
  return getCookie('deviceId');
}

/** @param {string} name */
function getCookie(name) {
  var matches = document.cookie.match(new RegExp(
    "(?:^|; )" + name.replace(/([.$?*|{}()\[\]\\\/+^])/g, '\\$1') + "=([^;]*)"
  ));
  return matches ? decodeURIComponent(matches[1]) : undefined;
}

export class ConnectionLostWidget {

  /** @param {string} template */
  constructor(template) {
    /** @type {?Element} */
    this._element = null;
    this._template = template;
  }

  show() {

    if (this._element !== null)
      return;

    // Parse template
    var element = document.createElement('div');
    element.innerHTML = this._template;
    element = element.children[0];

    // Append to document body
    document.body.appendChild(element);
    this._element = element;
  }

  hide() {
    if (this._element !== null) {
      document.body.removeChild(this._element);
      this._element = null;
    }
  }
}

export function throttle(func, timeFrame) {
  var lastTime = 0;
  return function () {
      var context = this, args = arguments;
      var now = new Date();
      if (now - lastTime >= timeFrame) {
          func.apply(context, args);
          lastTime = now;
      }
  };
}

export function debounce(func, wait, immediate) {
  var timeout;
  return function() {
  	var context = this, args = arguments;
  	clearTimeout(timeout);
  	timeout = setTimeout(function() {
  		timeout = null;
  		if (!immediate) func.apply(context, args);
  	}, wait);
  	if (immediate && !timeout) func.apply(context, args);
  };
}
