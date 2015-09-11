'use strict';

var _ = require('underscore'),
    Evaporate = require('evaporate'),
    uuid = require('node-uuid'),
    settings = require('../settings');

function S3UploadException(message, mimeType, fileName) {
    this.message = message || 'Failed to upload file.';
    this.mimeType = mimeType || 'type unknown';
    this.fileName = fileName || 'unknown file';
    this.name = 'S3UploadException';
    this.toString = function() {
        return this.message + ' File: ' + this.fileName + ' MimeType: ' + this.mimeType;
    };
}

var uploadFiles = function(files) {
    var evap = new Evaporate({
        signerUrl: settings.get('signerUrl'),
        aws_key: settings.get('awsKey'),
        bucket: settings.get('awsBucket'),
        logging: false
    });

    var invalidMimes = _.without(_.map(files, invalidTypes), null);
    if (invalidMimes.length > 0) {
        throw new S3UploadException('Invalid file type.', invalidMimes[0].mimeType, invalidMimes[0].fileName);
    }

    _.each(files, function(file) {
        var user = settings.getUser(),
            userId = user.get('id');
        // TODO Later we'll want to store the id of the file upload. We can use
        // it to cancel the upload if needed.
        // var id = evap.add({ //
        evap.add({
            // TODO - NAMES ARE CURRENTLY JUST FOR TESTING.
            // WE WANT TO USE A UUID FOR FILE NAMES.
            name: userId + '-' + uuid.v4() + getExtension(file),
            file: file,
            contentType: file.type,
            complete: function() {
                console.log('File upload complete');
            },
            progress: function(progress) {
                console.log('PROGRESS: ' + progress);
            }
        });
    });
};

var getExtension = function(file) {
    var mapping = {
        'image/png': '.png',
        'image/jpeg': '.jpg',
        'image/tiff': '.tif',
        'application/zip': '.zip'

    };
    return mapping[file.type] || '';
}

var invalidTypes = function(file) {
    var mimeType = file.type,
        fileName = file.name;

    switch (mimeType) {
        case 'image/png':
        case 'image/jpeg':
        case 'image/tiff':
        case 'application/zip':
            return null;

        default:
            return {
                mimeType: mimeType,
                fileName: fileName
            };
    }
};

module.exports = {
    uploadFiles: uploadFiles,
    S3UploadException: S3UploadException
};
