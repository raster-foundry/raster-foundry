/* global BUILDCONFIG HELPCONFIG */

class HomeController {
    constructor(authService, modalService, feedService) {
        'ngInject';
        this.authService = authService;
        this.modalService = modalService;
        this.feedService = feedService;
    }

    $onInit() {
        this.BUILDCONFIG = BUILDCONFIG;
        this.HELPCONFIG = HELPCONFIG;
        this.feedService.getPosts().then(posts => {
            this.blogPosts = posts;
        });
    }

    openCreateProjectModal() {
        return this.modalService.open({
            component: 'rfProjectCreateModal'
        });
    }

    openToolCreateModal() {
        return this.modalService.open({
            component: 'rfToolCreateModal'
        });
    }
}

export default HomeController;
