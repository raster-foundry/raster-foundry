'use strict';

var _ = require('underscore'),
    Evaporate = require('evaporate'),
    uuid = require('node-uuid'),
    settings = require('../settings'),
    coreSettings = require('../core/settings');

var uploadFiles = function(files) {
    var evap = new Evaporate({
        signerUrl: coreSettings.get('signerUrl'),
        aws_key: coreSettings.get('awsKey'),
        bucket: coreSettings.get('awsBucket'),
        logging: false
    });
    _.each(files, function(file) {
        var user = settings.getUser(),
            userId = user.get('id');
        // TODO Later we'll want to store the id of the file upload. We can use
        // it to cancel the upload if needed.
        // var id = evap.add({ //
        evap.add({
            // TODO - NAMES ARE CURRENTLY JUST FOR TESTING.
            // WE WANT TO USE A UUID FOR FILE NAMES.
            name: userId + '-' + uuid.v4(),
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
