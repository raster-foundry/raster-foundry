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
            this.deferred = $q.defer();
            this.projectRequest = this.deferred.promise;
            this.currentProjectId = null;
        }

        fetchCurrentProject() {
            return this.projectRequest;
        }

        setCurrentProject(id, force = false) {
            // Don't re-fetch the project if it has already been fetched unless
            // the force parameter is set to true
            if (this.currentProjectId !== id || force) {
                this.currentProjectId = id;
                this.projectRequest = this.projectService.loadProject(this.currentProjectId)
                // Some consumers may have a reference to the inital deferred object
                this.projectRequest.then(this.deferred.resolve, this.deferred.reject);
            }
            return this.projectRequest;
        }

    }

    app.service('projectEditService', ProjectEditService);
};
