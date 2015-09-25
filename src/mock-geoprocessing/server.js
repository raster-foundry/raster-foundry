var qs = require('querystring'),
    http = require('http');

var serverPort = 9500;

function handleNewLayer(request, response) {
    if (request.method == 'POST') {
        var body = '';
        request.on('data', function (data) {
            body += data;
        });
        request.on('end', function () {
            var parsedBody = qs.parse(body),
                delay = 3000;

            console.log('Received job_id: ' + parsedBody.job_id);
            setTimeout(function() {
                // TODO Do something to update status.
                console.log('Updating status for job_id: ' + parsedBody.job_id);
            }, delay);

            response.writeHead(200);
            response.end();
        });
    }
}

var server = http.createServer(handleNewLayer);
server.listen(serverPort, function() {
    console.log("Server listening on: http://localhost:%s", serverPort);
});
