'use strict';

var _ = require('underscore'),
    Evaporate = require('evaporate'),
    settings = require('../settings');

function S3UploadException(message, mimeType, fileName) {
    this.message = message || 'Failed to upload file.';
    this.mimeType = mimeType || 'type unknown';
    this.fileName = fileName || 'unknown file';
    this.name = 'S3UploadException';
    this.toString = function() {
        return this.message + ' File: ' + this.fileName +
            ' MimeType: ' + this.mimeType;
    };
}

var uploadFiles = function(fileDescriptions) {
    var evap = new Evaporate({
        signerUrl: settings.get('signerUrl'),
        aws_key: settings.get('awsKey'),
        bucket: settings.get('awsBucket'),
        logging: false
    });

    var aws_token = settings.get('awsToken');

    var files = _.pluck(fileDescriptions, 'file'),
        invalidMimes = _.without(_.map(files, invalidTypes), null);

    if (invalidMimes.length > 0) {
        throw new S3UploadException('Invalid file type.',
            invalidMimes[0].mimeType, invalidMimes[0].fileName);
    }

    _.each(fileDescriptions, function(fileDescription) {
        var userId = settings.getUser().get('id'),
            fileName = userId + '-' +
                fileDescription.uuid + '.' + fileDescription.extension,
            headers = {};

        if (aws_token) {
            headers['x-amz-security-token'] = aws_token;
        }

        evap.add({
            name: fileName,
            file: fileDescription.file,
            contentType: fileDescription.file.type,
            xAmzHeadersAtInitiate: headers,
            xAmzHeadersAtUpload: headers,
            xAmzHeadersAtComplete: headers,
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
        'image/png': 'png',
        'image/jpeg': 'jpg',
        'image/tiff': 'tif',
        'application/zip': 'zip'

    };
    return mapping[file.type] || '';
};

var invalidTypes = function(file) {
    var mimeType = file.type,
        fileName = file.name;

    switch (mimeType) {
        case 'image/tiff':
            return null;

        default:
            return {
                mimeType: mimeType,
                fileName: fileName
            };
    }
};

module.exports = {
    getExtension: getExtension,
    uploadFiles: uploadFiles,
    S3UploadException: S3UploadException,
    invalidTypes: invalidTypes
};
