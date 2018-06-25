export default (app) => {
    // Display organization status nicely for frontend
    app.filter('humanStatus', function () {
        'ngInject';
        return function (orgStatus) {
            switch (orgStatus) {
            case 'ACTIVE':
                return 'Active';
            case 'INACTIVE':
                return 'Inactive';
            case 'REQUESTED':
                return 'Requested';
            default:
                return '';
            }
        };
    });
};
