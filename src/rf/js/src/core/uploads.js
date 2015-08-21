'use strict';

var _ = require('underscore'),
    Evaporate = require('evaporate'),
    coreSettings = require('../core/settings');

var uploadFiles = function(files) {
    var evap = new Evaporate({
        signerUrl: coreSettings.get('signerUrl'),
        aws_key: coreSettings.get('awsKey'),
        bucket: coreSettings.get('awsBucket'),
        logging: false
    });
    _.each(files, function(file) {
        var id = evap.add({
            // TODO - NAMES ARE CURRENTLY JUST FOR TESTING.
            // WE WANT TO USE A UUID FOR FILE NAMES.
            name: Math.floor(Math.random() * 1000000) + '_' + file.name,
            file: file,
            complete: function() {
                console.log('File upload complete');
            },
            progress: function(progress) {
                console.log('PROGRESS: ' + progress);
            }
        });
    });
};

module.exports = {
    uploadFiles: uploadFiles
};
