const Map = require('es6-map');

export default class ProjectAddModalController {
    constructor(projectService, $log, $state) {
        'ngInject';

        this.projectService = projectService;
        this.$log = $log;
        this.$state = $state;

        this.projectList = [];
        this.populateProjectList(1);
        this.selectedProjects = new Map();
    }

    populateProjectList(page) {
        if (this.loading) {
            return;
        }
        delete this.errorMsg;
        this.loading = true;
        this.projectService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 5,
                page: page - 1
            }
        ).then((projectResult) => {
            this.lastProjectResult = projectResult;
            this.numPaginationButtons = 6 - projectResult.page % 5;
            if (this.numPaginationButtons < 3) {
                this.numPaginationButtons = 3;
            }
            this.currentPage = projectResult.page + 1;
            this.projectList = this.lastProjectResult.results;
            this.loading = false;
        }, () => {
            this.errorMsg = 'Server error.';
            this.loading = false;
        });
    }

    createNewProject(name) {
        delete this.newProjectName;
        this.projectService.createProject(name).then(
            () => {
                this.populateProjectList(this.currentPage);
            },
            (err) => {
                this.$log.error('Error creating project:', err);
            }
        );
    }

    isSelected(project) {
        return this.selectedProjects.has(project.id);
    }

    setSelected(project, selected) {
        if (selected) {
            this.selectedProjects.set(project.id, project);
        } else {
            this.selectedProjects.delete(project.id);
        }
    }

    addScenesToProjects() {
        let sceneIds = Array.from(this.resolve.scenes.keys());
        let numProjects = this.selectedProjects.size;
        this.selectedProjects.forEach((project, projectId) => {
            this.projectService.addScenes(projectId, sceneIds).then(
                () => {
                    this.selectedProjects.delete(projectId);
                    if (this.selectedProjects.size === 0) {
                        this.resolve.scenes.clear();
                        if (numProjects > 1) {
                            this.$state.go('library.projects.list');
                            this.close();
                        } else {
                            this.$state.go(
                                'library.projects.detail.scenes',
                                {projectid: projectId}
                            );
                            this.close();
                        }
                    }
                },
                (err) => {
                    // TODO: Show toast or error message instead of debug message
                    this.$log.debug(
                        'Error while adding scenes to project',
                        projectId, err
                    );
                }
            );
        });
    }

    allScenesAdded() {
        let allAdded = true;
        this.requests.forEach((sceneReqs) => {
            sceneReqs.forEach((isAdded) => {
                if (!isAdded) {
                    allAdded = false;
                }
            });
        });
        return allAdded;
    }
}
