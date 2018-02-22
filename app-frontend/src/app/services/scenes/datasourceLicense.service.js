const licenses = require('./all-licenses.json');
const license = require('./license.json');

export default (app) => {
  // NOTE: this mocks calls to licenses endpoint - just for temporary frontend support
  // TODO: will update once the licenses endpoint is ready
    class DatasourceLicenseService {
        constructor($log, $q) {
            'ngInject';

            this.$log = $log;
            this.$q = $q;
        }

        query(params = {}) {
            this.$log.log(params);

            let deferred = this.$q.defer();
            // eslint-disable-next-line
            deferred.resolve(JSON.stringify(licenses));
            return deferred.promise;
        }

        get(id) {
            this.$log.log(id);
            let deferred = this.$q.defer();
            // mock when there is a matched license
            // eslint-disable-next-line
            deferred.resolve(JSON.stringify(license));
            // mock when there is no matched license
            // deferred.resolve(JSON.stringify({}));
            return deferred.promise;
        }
    }

    app.service('datasourceLicenseService', DatasourceLicenseService);
};
