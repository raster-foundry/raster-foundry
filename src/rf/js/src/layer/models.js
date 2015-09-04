'use strict';

var Backbone = require('../../shim/backbone');

var Layer = Backbone.Model.extend({
    defaults: {
        name: '',
        organization: '',
        owner: 0,
        area: 0,
        capture_end: null,
        capture_start: null,
        srid: null
    }
});

module.exports = Layer;
