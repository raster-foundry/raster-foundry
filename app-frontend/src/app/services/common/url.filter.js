export default (app) => {
    // Restrict the display segments of an url
    app.filter('shortenUrl', function () {
        'ngInject';
        return function (url) {
            if (!url.length) {
                return '';
            }

            let segments = url.split('/');
            return `${segments[0]}//${segments[2]}/.../${url.substr(url.length - 15)}`;
        };
    });
};
