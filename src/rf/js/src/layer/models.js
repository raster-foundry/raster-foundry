'use strict';

var Backbone = require('../../shim/backbone');

var Layer = Backbone.Model.extend({
    defaults: {
        name: '',
        organization: '',
        owner: 0,
        area: 0,
        captureEndDate: null,
        captureStartDate: null,
        sourceDataProjection: null
    }
});

module.exports = Layer;
