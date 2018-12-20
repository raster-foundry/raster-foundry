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
        this.blogPosts = [];
        this.feedService.getPosts().then(posts => {
            this.blogPosts = posts;
        }).catch(() => {});
    }

    openCreateProjectModal() {
        this.modalService.open({
            component: 'rfProjectCreateModal'
        }).result.catch(() => {});
    }

    openTemplateCreateModal() {
        this.modalService.open({
            component: 'rfTemplateCreateModal'
        }).result.catch(() => {});
    }
}

export default HomeController;
