/* globals window*/
export default (app) => {
    class Storage {
        constructor(storage) {
            this.storage = storage;
        }

        // doesn't need to parse jsobject from string
        has(key) {
            return Boolean(this.storage.getItem(key));
        }

        get(key) {
            let value = this.storage.getItem(key);
            return value && JSON.parse(value);
        }

        set(key, value) {
            this.storage.setItem(key, JSON.stringify(value));
        }

        // Higher performance for raw strings
        getString(key) {
            return this.storage.getItem(key);
        }

        // Higher performance for raw strings
        setString(key, value) {
            this.storage.setItem(key, value);
        }

        remove(key) {
            this.storage.removeItem(key);
        }

        clear() {
            this.storage.clear();
        }

        length() {
            return this.storage.length;
        }
    }

    class SessionStorage extends Storage {
        constructor() {
            super(window.sessionStorage);
        }
    }

    class LocalStorage extends Storage {
        constructor() {
            super(window.localStorage);
        }
    }

    app.service('sessionStorage', SessionStorage);
    app.service('localStorage', LocalStorage);
};
