'use strict';

// Ensure that Backbone is required before Marionette.

var Backbone = require('./backbone'),
    Marionette = require('backbone.marionette');

// Expose Backbone and Marionette for the Marionette Inspector
if (window.__agent) {
    window.__agent.start(Backbone, Marionette);
}

module.exports = Marionette;
