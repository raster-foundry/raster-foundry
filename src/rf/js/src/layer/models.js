'use strict';

var Backbone = require('../../shim/backbone');

var Layer = Backbone.Model.extend({
    defaults: {
        name: '',
        organization: '',
        owner: 0
    }
});

module.exports = Layer;
