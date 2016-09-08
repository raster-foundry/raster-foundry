$(function () {
    var url = window.location.protocol + '//' + window.location.host + '/docs/swagger/spec.yml';

    // Pre load translate...
    if(window.SwaggerTranslator) {
        window.SwaggerTranslator.translate();
    }

    $('pre code').each(function(i, e) {
        hljs.highlightBlock(e);
    });

    window.swaggerUi = new SwaggerUi({
        url: url,
        dom_id: "swagger-ui-container",
        supportedSubmitMethods: ['get', 'post', 'put', 'delete', 'patch'],
        onComplete: function(swaggerApi, swaggerUi) {
            // set host dynamically so that the same spec works in dev
            swaggerApi.setHost(window.location.host);
        },
        onFailure: function(data) {
            log("Unable to Load SwaggerUI");
        },
        docExpansion: "none",
        jsonEditor: false,
        defaultModelRendering: 'schema',
        showRequestHeaders: false
    });

    function addApiKeyAuthorization() {
        var key = encodeURIComponent($('#input_apiKey')[0].value);
        if(key && key.trim() != "") {
            var apiKeyAuth = new SwaggerClient.ApiKeyAuthorization("Authorization", "Bearer " + key, "header");
            window.swaggerUi.api.clientAuthorizations.add("api_key", apiKeyAuth);
            log("added key " + key);
        }
    }

    function updateApiHost() {
        var host = encodeURIComponent($('#input_apiHost')[0].value) || window.location.host;
        window.swaggerUi.api.setHost(host);
    }

    $('#input_apiKey').change(addApiKeyAuthorization);
    $('#input_apiHost').change(updateApiHost);

    window.swaggerUi.load();
    function log() {
        if ('console' in window) {
            console.log.apply(console, arguments);
        }
    }
});
