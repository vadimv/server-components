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

const MIN_RECONNECT_TIMEOUT = 200;
const MAX_RECONNECT_TIMEOUT = 5000;
const RSP_TRANSPORT_VERSION = 1;

/** @enum {number} */
const ClientControlType = {
  RESUME: 7,
  ACKNOWLEDGE: 8,
  TERMINATE: 9
};

/** @enum {number} */
const ServerTransportType = {
  FRAME: 17,
  RESUME_ACCEPTED: 18,
  RESUME_REJECTED: 19
};

/**
 * A reconnectable WebSocket whose application channel becomes open only after
 * the local server session has replayed all missed messages.
 */
export class Connection {

  /**
   * @param {string} deviceId
   * @param {string} sessionId
   * @param {string} serverRootPath
   * @param {Location} location
   */
  constructor(deviceId, sessionId, serverRootPath, location) {
    this._deviceId = deviceId;
    this._sessionId = sessionId;
    this._serverRootPath = serverRootPath;
    this._hostPort = location.host;
    this._useSSL = location.protocol === "https:";

    /** @type {?WebSocket} */
    this._webSocket = null;
    /** @type {?number} */
    this._reconnectTimer = null;
    this._reconnectTimeout = MIN_RECONNECT_TIMEOUT;
    this._generation = 0;
    this._attempted = false;
    this._state = 'closed';
    this._lastAppliedSequence = 0;
    this._dispatcher = window.document.createDocumentFragment();
  }

  get dispatcher() { return this._dispatcher }
  get state() { return this._state }

  /**
   * @param {string} type
   * @private
   * @return {!Event}
   */
  _createEvent(type) {
    if (typeof Event === "function") {
      return new Event(type);
    } else {
      let event = document.createEvent('Event');
      event.initEvent(type, false, false);
      return event;
    }
  }

  /** @private */
  _connectUsingWebSocket() {
    if (window.WebSocket === undefined) {
      this._resumeRejected('websocket-not-supported');
      return;
    }

    let url = (this._useSSL ? "wss://" : "ws://") + this._hostPort;
    let path = this._serverRootPath + `bridge/web-socket/${this._deviceId}/${this._sessionId}`;
    let uri = url + path;
    let generation = ++this._generation;
    let webSocket = new WebSocket(uri);
    this._webSocket = webSocket;

    webSocket.addEventListener('open', () => this._onRawOpen(webSocket, generation));
    webSocket.addEventListener('close', () => this._onRawClose(webSocket, generation));
    webSocket.addEventListener('error', () => this._onRawError(webSocket, generation));
    webSocket.addEventListener('message', (event) => this._onRawMessage(webSocket, generation, event.data));

    console.log(`Trying to open connection to ${uri} using WebSocket`);
  }

  /** @private */
  _onRawOpen(webSocket, generation) {
    if (!this._isCurrent(webSocket, generation)) return;
    console.log('WebSocket opened; resuming page session');
    this._state = 'handshaking';
    webSocket.send(JSON.stringify([
      ClientControlType.RESUME,
      RSP_TRANSPORT_VERSION,
      this._lastAppliedSequence
    ]));
  }

  /** @private */
  _onRawError(webSocket, generation) {
    if (!this._isCurrent(webSocket, generation)) return;
    console.log('Connection error');
    this._dispatcher.dispatchEvent(this._createEvent('error'));
  }

  /** @private */
  _onRawClose(webSocket, generation) {
    if (!this._isCurrent(webSocket, generation)) return;
    console.log('Connection closed');
    this._webSocket = null;
    if (this._state === 'closed') return;
    this._state = 'closed';
    this._dispatcher.dispatchEvent(this._createEvent('close'));
  }

  /** @private */
  _onRawMessage(webSocket, generation, data) {
    if (!this._isCurrent(webSocket, generation)) return;

    let message;
    try {
      message = JSON.parse(data);
    } catch (error) {
      this._resumeRejected('invalid-server-message');
      return;
    }
    if (!(message instanceof Array) || message.length === 0) {
      this._resumeRejected('invalid-server-message');
      return;
    }

    switch (message[0]) {
      case ServerTransportType.FRAME:
        this._onApplicationFrame(message);
        break;
      case ServerTransportType.RESUME_ACCEPTED:
        this._onResumeAccepted(message);
        break;
      case ServerTransportType.RESUME_REJECTED:
        this._resumeRejected(message.length > 1 ? String(message[1]) : 'resume-rejected');
        break;
      default:
        // Allows a server without the local session to issue the legacy reload command.
        this._dispatchApplicationMessage(data, null);
        break;
    }
  }

  /** @private */
  _onApplicationFrame(message) {
    if (message.length !== 3 || !Number.isSafeInteger(message[1]) || message[1] < 1
        || !(message[2] instanceof Array)) {
      this._resumeRejected('invalid-server-frame');
      return;
    }
    let sequence = message[1];
    if (sequence <= this._lastAppliedSequence) {
      this._sendAcknowledgement();
      return;
    }
    if (sequence !== this._lastAppliedSequence + 1) {
      this._resumeRejected('server-frame-gap');
      return;
    }
    this._dispatchApplicationMessage(JSON.stringify(message[2]), sequence);
  }

  /** @private */
  _onResumeAccepted(message) {
    if (message.length !== 2 || !Number.isSafeInteger(message[1])
        || message[1] !== this._lastAppliedSequence) {
      this._resumeRejected('incomplete-server-replay');
      return;
    }
    this._state = 'open';
    this._attempted = true;
    this._reconnectTimeout = MIN_RECONNECT_TIMEOUT;
    console.log('Page session resumed');
    this._dispatcher.dispatchEvent(this._createEvent('open'));
  }

  /** @private */
  _dispatchApplicationMessage(data, sequence) {
    let event = this._createEvent('message');
    event.data = data;
    event.sequence = sequence;
    this._dispatcher.dispatchEvent(event);
  }

  /** @private */
  _resumeRejected(reason) {
    console.log(`Page session resume rejected: ${reason}`);
    let event = this._createEvent('resume-rejected');
    event.reason = reason;
    this._dispatcher.dispatchEvent(event);
  }

  /** @private */
  _isCurrent(webSocket, generation) {
    return this._webSocket === webSocket && this._generation === generation;
  }

  /**
   * Called by the bridge only after it has successfully applied a sequenced command.
   * @param {number} sequence
   */
  applied(sequence) {
    if (!Number.isSafeInteger(sequence) || sequence !== this._lastAppliedSequence + 1) {
      this._resumeRejected('invalid-applied-sequence');
      return;
    }
    this._lastAppliedSequence = sequence;
    this._sendAcknowledgement();
  }

  /** @private */
  _sendAcknowledgement() {
    if (this._webSocket !== null && this._webSocket.readyState === WebSocket.OPEN) {
      this._webSocket.send(JSON.stringify([
        ClientControlType.ACKNOWLEDGE,
        this._lastAppliedSequence
      ]));
    }
  }

  /**
   * Sends an application message only while the socket is attached or replaying.
   * @param {string} data
   * @return {boolean}
   */
  send(data) {
    if (this._webSocket === null || this._webSocket.readyState !== WebSocket.OPEN
        || (this._state !== 'open' && this._state !== 'handshaking')) {
      return false;
    }
    this._webSocket.send(data);
    return true;
  }

  /**
   * Closes this browser connection. A terminal close asks the server to release
   * the local page immediately; delivery is best effort during page unload.
   * @param {boolean=} terminal
   */
  disconnect(terminal = true) {
    if (this._reconnectTimer !== null) {
      clearTimeout(this._reconnectTimer);
      this._reconnectTimer = null;
    }
    if (this._webSocket !== null) {
      if (terminal && this._webSocket.readyState === WebSocket.OPEN) {
        this._webSocket.send(JSON.stringify([ClientControlType.TERMINATE]));
      }
      this._webSocket.close();
    } else {
      this._state = 'closed';
    }
  }

  connect() {
    if (this._state === 'connecting' || this._state === 'handshaking' || this._state === 'open'
        || this._reconnectTimer !== null) {
      return;
    }

    this._state = 'connecting';
    let isFirstAttempt = !this._attempted;
    let delay = isFirstAttempt ? 0 : this._reconnectTimeout;
    this._attempted = true;
    let event = this._createEvent('connecting');
    this._dispatcher.dispatchEvent(event);
    this._reconnectTimer = setTimeout(() => {
      this._reconnectTimer = null;
      this._connectUsingWebSocket();
    }, delay);
    if (!isFirstAttempt) {
      this._reconnectTimeout = Math.min(this._reconnectTimeout * 2, MAX_RECONNECT_TIMEOUT);
    }
  }
}
