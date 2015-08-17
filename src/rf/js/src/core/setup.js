'use strict';

// Setup shims and require third party dependencies in the correct order.
// For example, jQuery needs to exist before requiring boostrap.

// Global jQuery needed for Bootstrap plugins.
var $ = require('jquery');
window.jQuery = window.$ = $;

// Marionette attaches itself to Backbone so these must be required in
// this order.
// Instead of requiring both Backbone and Marionette in every module that
// needs one or the other, explicitly require them here in the correct
// order, so they work predictably everywhere.
require('../../shim/backbone');
require('../../shim/backbone.marionette');

require('bootstrap');

var csrf = require('./csrf');
$.ajaxSetup(csrf.jqueryAjaxSetupOptions);
