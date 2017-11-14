/* global BUILDCONFIG */

class HomeController {
    constructor(authService, $uibModal, feedService) {
        'ngInject';
        this.authService = authService;
        this.$uibModal = $uibModal;
        this.feedService = feedService;
    }

    $onInit() {
        this.BUILDCONFIG = BUILDCONFIG;
        this.feedService.getPosts().then(posts => {
            this.blogPosts = posts;
        });
    }

    $onDestroy() {

    }

    openCreateProjectModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfProjectCreateModal'
        });

        this.activeModal.result.then(() => {

        });

        return this.activeModal;
    }

    openToolCreateModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfToolCreateModal'
        });
    }
}

export default HomeController;
