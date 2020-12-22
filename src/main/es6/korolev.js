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

import { getDeviceId, throttle, debounce } from './utils.js';

/** @enum {number} */
export const CallbackType = {
  DOM_EVENT: 0, // `$renderNum:$elementId:$eventType`
  CUSTOM_CALLBACK: 1, // `$name:$arg`
  EXTRACT_PROPERTY_RESPONSE: 2, // `$descriptor:$propertyType:$value`
  HISTORY: 3, // URL
  EVALJS_RESPONSE: 4, // `$descriptor:$status:$value`
  EXTRACT_EVENT_DATA_RESPONSE: 5, // `$descriptor:$dataJson`
  HEARTBEAT: 6 // `$descriptor`
};

/** @enum {number} */
export const PropertyType = {
  STRING: 0,
  NUMBER: 1,
  BOOLEAN: 2,
  OBJECT: 3,
  ERROR: 4
};

/** @enum {number} */
export const LocationType = {
  HREF: 0,
  PATHNAME: 1,
  HASH: 2,
  SEARCH: 3,
  PUSH_STATE: 4
};

/** @enum {number} */
export const EventModifierType = {
  NO_EVENT_MODIFIER: 0,
  THROTTLE_EVENT_MODIFIER: 1,
  DEBOUNCE_EVENT_MODIFIER: 2
};

/** @enum {string} */
export const VirtualDomPaths = {
  WINDOW_PATH: '0',
  DOCUMENT_PATH: '1'
};

export class Korolev {

  /**
   * @param {Object} config
   * @param {function(CallbackType, string)} callback
   */
  constructor(config, callback) {
    /** @type {Object} */
    this.config = config;
    /** @type {HTMLElement} */
    this.root = document.children[0];
    /** @type {Object<Element>} */
    this.els = {};
    /** @type {number} */
    this.renderNum = 0;
    /** @type {Object} */
    this.listeners = {};
    /** @type {?function(Event)} */
    this.historyHandler = null;
    /** @type {string} */
    this.initialPath = window.location.pathname;
    /** @type {function(CallbackType, string)} */
    this.callback = callback;
    /** @type {Array} */
    this.eventData = [];

    this.historyHandler = (/** @type {Event} */ event) => {
      callback(CallbackType.HISTORY, window.location.pathname + window.location.hash);
    };

    window.vId = VirtualDomPaths.WINDOW_PATH;
    document.vId = VirtualDomPaths.DOCUMENT_PATH;

    this.listen = (target, name, preventDefault, eventModifier) => {
      var listener = (event) => {
        if (event.target.vId) {
          if (preventDefault) {
            event.preventDefault();
          }
          this.eventData[this.renderNum] = event;

          this.callback(CallbackType.DOM_EVENT,
                        this.renderNum + ':' + event.target.vId + ':' + event.type,
                        this.eventObject(event.type, event));
        }
      };

      /**
       * Add some specific properties of an event object for some specific event types
       */
      this.eventObject = (eventType, e) => {
        var result = {};
        if (eventType == 'keydown') {
            result.keyCode = '' + e.keyCode;
        } else if (eventType == 'popstate') {
            result.path = window.location.pathname;
            result.hash = window.location.hash;
        } else if (eventType == 'submit') {
            var formData = new FormData(e.target);
            formData.forEach(function(value, key) {
                result[key] = value;
            });
        }
        return result;
      }

      this.createEventModifier = (eventModifier, listener) => {
         let mArray = eventModifier.split(':');
         let eventModifierType = parseInt(mArray[0]);
         if (eventModifierType === EventModifierType.THROTTLE_EVENT_MODIFIER) {
            return throttle(listener, parseInt(mArray[1]));
         } else if(eventModifierType === EventModifierType.DEBOUNCE_EVENT_MODIFIER) {
            return debounce(listener, parseInt(mArray[1]), mArray[2] === 'true');
         }
      }

      let me = eventModifier && eventModifier != EventModifierType.NO_EVENT_MODIFIER.toString();
      target.addEventListener(name, me ? this.createEventModifier(eventModifier, listener) : listener);
      let eventKey = target.vId + '-' + name;
      this.listeners[eventKey] = { 'target': target, 'listener': listener, 'type': name };
    };
  }

  swapElementInRegistry(a, b) {
    b.vId = a.vId;
    this.els[a.vId] = b;
  }

  destroy() {
    // Remove listeners
    Object.keys(this.listeners).forEach((key) => this.listeners[key].target.removeEventListener(this.listeners[key].type,
                                                                                                this.listeners[key].listener));
  }
  
  /** @param {number} n */
  setRenderNum(n) {
    // Remove obsolete event data
    delete this.eventData[n - 2];
    this.renderNum = n;
  }

  /** @param {HTMLElement} rootNode */
  registerRoot(rootNode) {
    let self = this;
    function aux(prefix, node) {
      var children = node.childNodes;
      for (var i = 0; i < children.length; i++) {
        var child = children[i];
        var id = prefix + '_' + (i + 1);
        child.vId = id;
        self.els[id] = child;
        aux(id, child);
      }
    }
    self.root = rootNode;
    self.els[VirtualDomPaths.DOCUMENT_PATH] = rootNode;
    aux(VirtualDomPaths.DOCUMENT_PATH, rootNode);
  }

  cleanRoot() {
    while (this.root.children.length > 0)
      this.root.removeChild(this.root.children[0]);
  }

   /**
    * @param {string} type
    * @param {boolean} preventDefault
    * @param {string} path
    * @param {string} eventModifier
    */
  listenEvent(type, preventDefault, path, eventModifier) {
    let target = path === window.vId ? window : this.els[path];
    this.listen(target, type, preventDefault, eventModifier);
  }

    /**
    * @param {string} type
    * @param {string} path
    */
  forgetEvent(type, path) {
    let target = path === window.vId ? window : this.els[path];
    let eventKey = path + '-' + type;
    let eventEntry = this.listeners[eventKey];
    eventEntry.target.removeEventListener(type, eventEntry.listener);
    delete this.listeners[eventKey];
  }

  /**
   * @param {Array} data
   */
  modifyDom(data) {
    // Reverse data to use pop() instead of shift()
    // pop() faster than shift()
    let atad = data.reverse();
    let r = atad.pop.bind(atad);
    while (data.length > 0) {
      switch (r()) { // TODO constants
        case 0: this.create(r(), r(), r(), r()); break;
        case 1: this.createText(r(), r(), r()); break;
        case 2: this.remove(r(), r()); break;
        case 3: this.setAttr(r(), r(), r(), r(), r()); break;
        case 4: this.removeAttr(r(), r(), r(), r()); break;
        case 5: this.setStyle(r(), r(), r()); break;
        case 6: this.removeStyle(r(), r()); break;
      }
    }
  }
  
   /**
    * @param {string} id
    * @param {string} childId
    * @param {string} tag
    */
  create(id, childId, xmlNs, tag) {
    var parent = this.els[id],
      child = this.els[childId],
      newElement;
    if (!parent) return;
    if (xmlNs === 0) {
      newElement = document.createElement(tag);
    } else {
      newElement = document.createElementNS(xmlNs, tag);
    }
    newElement.vId = childId;
    if (child && child.parentNode === parent) {
      parent.replaceChild(newElement, child);
    } else {
      parent.appendChild(newElement);
    }
    this.els[childId] = newElement;
  }

   /**
    * @param {string} id
    * @param {string} childId
    * @param {string} text
    */
  createText(id, childId, text) {
    var parent = this.els[id],
      child = this.els[childId],
      newElement;
    if (!parent) return;
    newElement = document.createTextNode(text);
    newElement.vId = childId;
    if (child && child.parentNode === parent) {
      parent.replaceChild(newElement, child);
    } else {
      parent.appendChild(newElement);
    }
    this.els[childId] = newElement;
  }

   /**
    * @param {string} id
    * @param {string} childId
    */
  remove(id, childId) {
    var parent = this.els[id],
      child = this.els[childId];
    if (!parent) return;
    if (child) {
      parent.removeChild(child);
    }
  }

   /**
    * @param {string} descriptor
    * @param {string} id
    * @param {string} propertyName
    */
  extractProperty(descriptor, id, propertyName) {
    let element = id === '1' ? window : this.els[id];
    let value = element[propertyName];
    var result, type;
    switch (typeof value) {
      case 'undefined':
        type = PropertyType.ERROR;
        result = `${propertyName} is undefined`;
        break;
      case 'function':
        type = PropertyType.ERROR;
        result = `${propertyName} is a function`;
        break;
      case 'object':
        type = PropertyType.OBJECT;
        result = value;
        break;
      case 'string':
        type = PropertyType.STRING;
        result = value;
        break;
      case 'number':
        type = PropertyType.NUMBER;
        result = value;
        break;
      case 'boolean':
        type = PropertyType.BOOLEAN;
        result = value;
        break;
    }
    this.callback(
        CallbackType.EXTRACT_PROPERTY_RESPONSE,
         `${descriptor}:${type}`,
          result);
  }

   /**
    * @param {string} id
    * @param {string} name
    * @param {string} value
    * @param {boolean} isProperty
    */
  setAttr(id, xmlNs, name, value, isProperty) {
    var element = id == '1' ? window : this.els[id];
    if (isProperty) element[name] = value;
    else if (xmlNs === 0) {
      element.setAttribute(name, value);
    } else {
      element.setAttributeNS(xmlNs, name, value);
    }
  }

   /**
    * @param {string} id
    * @param {string} name
    * @param {boolean} isProperty
    */
  removeAttr(id, xmlNs, name, isProperty) {
    var element = this.els[id];
    if (isProperty) element[name] = undefined;
    else if (xmlNs === 0) {
      element.removeAttribute(name);
    } else {
      element.removeAttributeNS(xmlNs, name);
    }
  }

   /**
    * @param {string} id
    * @param {string} name
    * @param {string} value
    */
  setStyle(id, name, value) {
    var element = this.els[id];
    element.style[name] = value;
  }

   /**
    * @param {string} id
    * @param {string} name
    */
  removeStyle(id, name) {
    var element = this.els[id];
    element.style[name] = null;
  }

   /**
    * @param {string} id
    */
  focus(id) {
    setTimeout(() => {
      var element = this.els[id];
      element.focus();
    }, 0);
  }

   /**
    * @param {string} id
    */
  element(id) {
    return this.els[id];
  }

   /**
    * @param {number} locationType
    * @param {string} path
    */
  setLocation(locationType, path) {
    switch(locationType) {
        case LocationType.HREF:
            window.location.href = path;
        break;
        case LocationType.PATHNAME:
            window.location.pathname = path;
        break;
        case LocationType.HASH:
            window.location.hash = path;
        break;
        case LocationType.SEARCH:
            window.location.search = path;
        break;
        case LocationType.PUSH_STATE:
            if (path !== window.location.pathname) window.history.pushState(path,"", path);
        break;
    }
  }

   /**
    * @param {string} name
    * @param {string} arg
    */
  invokeCustomCallback(name, arg) {
    this.callback(CallbackType.CUSTOM_CALLBACK, [name, arg].join(':'));
  }

   /**
    * @param {string} id
    * @param {string} descriptor
    */
  uploadForm(id, descriptor) {
    let self = this;
    var form = self.els[id];
    var formData = new FormData(form);
    var request = new XMLHttpRequest();
    var deviceId = getDeviceId();
    var uri = self.config['r'] +
      'bridge' +
      '/' + deviceId +
      '/' + self.config['sid'] +
      '/form-data' +
      '/' + descriptor;
    request.open("POST", uri, true);
//    request.upload.onprogress = function(event) {
//      var arg = [descriptor, event.loaded, event.total].join(':');
//      self.callback(CallbackType.FORM_DATA_PROGRESS, arg);
//    };
    request.send(formData);
  }

  /**
    * @param {string} id
    * @param {string} descriptor
    */
  listFiles(id, descriptor) {
    let self = this;
    let input = self.els[id];
    let deviceId = getDeviceId();
    let files = [];
    let uri = self.config['r'] +
      'bridge' +
      '/' + deviceId +
      '/' + self.config['sid'] +
      '/file' +
      '/' + descriptor;
    for (var i = 0; i < input.files.length; i++) {
      files.push(input.files[i]);
    }
    // Send first request with information about files
    let request = new XMLHttpRequest();
    request.open('POST', uri + "/info", true);
    request.send(files.map((f) => `${f.name}/${f.size}`).join('\n'));
  }

  /**
   * @param {string} id
   * @param {string} descriptor
   * @param {string} fileName
   */
  uploadFile(id, descriptor, fileName) {
    let self = this;
    let input = self.els[id];
    let deviceId = getDeviceId();
    let uri = self.config['r'] +
        'bridge' +
        '/' + deviceId +
        '/' + self.config['sid'] +
        '/file' +
        '/' + descriptor;
    var file = null;

    for (var i = 0; i < input.files.length; i++) {
      if(input.files[i].name == fileName) {
        file = input.files[i];
      }
    }

    if(file) {
      let request = new XMLHttpRequest();
      request.open('POST', uri, true);
      request.setRequestHeader('x-name', file.name)
      request.send(file);
    } else {
      console.error(`Can't find file with name ${fileName}`);
    }
  }

  resetForm(id) {
    let element = this.els[id];
    element.reset();
  }

  reloadCss() {
    var links = document.getElementsByTagName("link");
    for (var i = 0; i < links.length; i++) {
      var link = links[i];
      if (link.getAttribute("rel") === "stylesheet")
        link.href = link.href + "?refresh=" + new Date().getMilliseconds();
    }
  }

  /**
   * @param {string} descriptor
   * @param {string} code
   */
  evalJs(descriptor, code) {
    var result;
    var status = 0;
    try {
      result = eval(code);
    } catch (e) {
      console.error(`Error evaluating code ${code}`, e);
      result = e;
      status = 1;
    }

    if (result instanceof Promise) {
      result.then(
        (res) => this.callback(CallbackType.EVALJS_RESPONSE,`${descriptor}:0`, res),
        (err) => {
          console.error(`Error evaluating code ${code}`, err);
          this.callback(CallbackType.EVALJS_RESPONSE,`${descriptor}:1:err}`)
        }
      );
    } else {
      if (status === 1) result = result.toString();
      this.callback(
        CallbackType.EVALJS_RESPONSE,
        `${descriptor}:${status}`,
        result
      );
    }
  }

  extractEventData(descriptor, renderNum) {
    let data = this.eventData[renderNum];
    let result = {};
    for (let propertyName in data) {
      let value = data[propertyName];
      switch (typeof value) {
        case 'string':
        case 'number':
        case 'boolean':
          result[propertyName] = value;
          break;
        case 'object':
          if (propertyName === 'detail') {
            result[propertyName] = value;
          }
          break;
        default: // do nothing
      }
    }
    this.callback(
      CallbackType.EXTRACT_EVENT_DATA_RESPONSE,
      `${descriptor}:${JSON.stringify(result)}`
    );
  }
}
