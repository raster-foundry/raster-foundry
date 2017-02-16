export default (app) => {
    class ThumbnailService {
        constructor(authService) {
            'ngInject';
            this.authService = authService;
        }

        getBestFitUrl(thumbnails, size) {
            let url = thumbnails.reduce((thumb, next) => {
                if (Math.abs(size - next.widthPx) < Math.abs(size - thumb.widthPx)) {
                    return next;
                }
                return thumb;
            }).url;
            if (url.startsWith('/')) {
                return `${url}?token=${this.authService.token()}`;
            }
            return url;
        }
    }

    app.service('thumbnailService', ThumbnailService);
};
