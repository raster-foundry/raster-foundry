export default (app) => {
    class ProjectEditService {
        constructor(
            $q, projectService
        ) {
            this.projectService = projectService;
            // A consumer may request the project before it has been set.
            // We create the deferred object here and resolve later so that
            // all consumers regardless of timing get a promise that will
            // eventually be resolved. Once a project is set as the current
            // project, this deferred object will be overwritten with that
            // request, ensuring that all consumers get the most recent
            // current project.
            this.$q = $q;
            this.projectPromises = [];
            this.currentProjectId = null;
            this.currentProject = null;
            this.projectFetchError = null;
        }

        fetchCurrentProject() {
            return this.$q((resolve, reject) => {
                if (this.currentProjectId && this.projectRequest) {
                    this.projectRequest.then(resolve, reject);
                } else if (this.currentProject) {
                    resolve(this.currentProject);
                } else {
                    this.projectPromises.push({resolve: resolve, reject: reject});
                }
            });
        }

        setCurrentProject(id, force = false) {
            // Don't re-fetch the project if it has already been fetched unless
            // the force parameter is set to true
            return this.$q((resolve, reject) => {
                if (this.currentProjectId !== id || force) {
                    this.currentProjectId = id;
                    this.projectRequest = this.projectService.fetchProject(this.currentProjectId);
                    this.projectRequest.then((project) => {
                        this.currentProject = project;
                        this.projectPromises.forEach((promise) => {
                            promise.resolve(project);
                        });
                        resolve(project);
                    }, (error) => {
                        this.projectFetchError = error;
                        this.projectPromises.forEach((promise) => {
                            promise.reject(error);
                        });
                        reject(error);
                    });
                }
            });
        }

        updateCurrentProject(params) {
            return this.$q((resolve, reject) => {
                this.projectService.updateProject(params).then(() => {
                    this.setCurrentProject(this.currentProjectId, true)
                        .then((project) => {
                            resolve(project);
                        }, (error) => {
                            const message = 'Error fetching updated project from API';
                            reject({message: message, error});
                        });
                }, (error) => {
                    reject({message: 'Error updating project', error});
                });
            });
        }
    }

    app.service('projectEditService', ProjectEditService);
};
