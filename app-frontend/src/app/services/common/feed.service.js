/* globals BUILDCONFIG */

export default (app) => {
    class FeedService {
        constructor($resource, $q, $http) {
            'ngInject';

            this.$q = $q;
            this.$http = $http;
        }

        getPosts() {
            return this.$q((resolve, reject) => {
                if (!BUILDCONFIG.FEED_SOURCE || BUILDCONFIG.FEED_SOURCE === 'disabled') {
                    reject();
                } else {
                    this.$http({
                        method: 'GET',
                        url: `${BUILDCONFIG.API_HOST}/api/feed/` +
                            `?source=${BUILDCONFIG.FEED_SOURCE}`
                    }).then(response => {
                        let raw = response.data;
                        try {
                            let json = JSON.parse(raw.substring(raw.indexOf('{')));
                            let payload = json.payload;
                            if (payload && payload.posts) {
                                resolve(payload.posts);
                            } else {
                                reject();
                            }
                        } catch (err) {
                            reject();
                        }
                    }, () => {
                        reject();
                    });
                }
            });
        }
    }

    app.service('feedService', FeedService);
};
