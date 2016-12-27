export default (app) => {
    class ThumbnailService {
        getBestFit(thumbnails, size) {
            return thumbnails.reduce((thumb, next) => {
                if (Math.abs(size - next.widthPx) < Math.abs(size - thumb.widthPx)) {
                    return next;
                }
                return thumb;
            });
        }
    }

    app.service('thumbnailService', ThumbnailService);
};
