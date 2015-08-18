'use strict';

// Ensure that Backbone.$ is defined for Marionette.

var $ = require('jquery'),
    Backbone = require('backbone');

// See: https://github.com/jashkenas/backbone/issues/3291
Backbone.$ = $;

module.exports = Backbone;
