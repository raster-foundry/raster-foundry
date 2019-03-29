export default (app) => {
    class ShareService {
        constructor() {
            this.showingAnnotations = false;
            this.callbacks = {
                toggleShowingAnnotations: {}
            };
        }

        toggleShowingAnnotations(value = !this.showingAnnotations) {
            this.showingAnnotations = value;
            Object.values(this.callbacks.toggleShowingAnnotations).forEach(c => c(value));
        }

        addCallback(key, callback) {
            const id = new Date().getTime();
            this.callbacks[key][id] = callback;
            return id;
        }

        removeCallback(key, id) {
            delete this.callbacks[key][id];
        }
    }

    app.service('shareService', ShareService);
};
