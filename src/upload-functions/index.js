'use strict';

var AWS = require('aws-sdk'),
    util = require('util'),
    unzip = require('unzip');

var DESTINATION_BUCKET = 'raster-foundry-verified-zp0mnkphjtoxakytsoz5gqmwshmx8';

// get reference to S3 client
var s3 = new AWS.S3();

function isImage(extension) {
    return extension === 'jpg' || extension === 'png' || extension === 'tif';
}

function isZip(extension) {
    return extension === 'zip';
}

function allowedType(extension) {
    return isImage(extension) || isZip(extension);
}

exports.handler = function(event, context) {
    // Read options from the event.
    console.log('Reading options from event:\n', util.inspect(event, {depth: 5}));
    var srcBucket = event.Records[0].s3.bucket.name;

    // We try to prevent this but just in case, get rid of spaces or unicode
    // non-ASCII characters.
    var srcKey = decodeURIComponent(event.Records[0].s3.object.key.replace(/\+/g, ' '));

    // Sanity check: validate that source and destination are different buckets.
    if (srcBucket == DESTINATION_BUCKET) {
        console.error("Destination bucket must not match source bucket.");
        return;
    }

    // Infer the image type.
    var typeMatch = srcKey.match(/\.([^.]*)$/);
    if (!typeMatch) {
        console.error('No file extension on file: ' + srcKey);
        return;
    }
    var fileExtension = typeMatch[1];

    if (!allowedType(fileExtension)) {
        console.log('Skipping unknown file type: ' + srcKey);
        return;
    }

    if (isImage(fileExtension)) {
        // TODO verify this image is good.
        var copyParams = {
            Bucket: DESTINATION_BUCKET,
            CopySource: srcBucket + '/' + srcKey,
            Key: srcKey
        };
        s3.copyObject(copyParams, function(err, data) {
            if (err) {
                console.log('File transfer FAILED.');
                context.done(err);
            } else {
                console.log('File transfer complete!');
                context.done(null, data);
            }
        });
    } else if (isZip(fileExtension)) {
        // Unzip and move.
        // TODO verify file is valid.
        var sourceParams = {
            Bucket: srcBucket,
            Key: srcKey
        };

        var parseStream = s3.getObject(sourceParams)
            .createReadStream()
            .pipe(unzip.Parse());

        parseStream.on('entry', function(entry) {
            console.log('STARTED ' + entry.path);
            // TODO check type.
            // TODO make file names UUIDs and prefix with user id.
            // TODO replace file extensions with our own after file checks.
            var fileName = entry.path,
                type = entry.type,
                uploadParams = {
                    Bucket: DESTINATION_BUCKET,
                    Key: fileName,
                    Body: entry,
                    ContentType: 'application/zip'
                };

            s3.upload(uploadParams, function(err, data) {
                if (err) {
                    console.log('File transfer FAILED.');
                    context.done(err);
                } else {
                    console.log('Extracted and moved file: ' + fileName);
                }
            });
        });

        parseStream.on('close', function(err) {
            context.done(null, 'File extraction complete.');
        });
    }
};
