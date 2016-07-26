export default function (app) {
    function resolverProvider() {
        this.$get = function () {
            return this;
        };
    }

    app.provider('resolver', resolverProvider);
}
