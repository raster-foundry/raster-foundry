/* eslint-disable quotes */
/* global _ */

const statusMapping = {
    "export": {
        "NOTEXPORTED": {
            "label": "Export in progress",
            "color": "grey"
        },
        "TOBEEXPORTED": {
            "label": "Queued for export",
            "color": "yellow"
        },
        "EXPORTING": {
            "label": "Currently being exported",
            "color": "yellow"
        },
        "EXPORTED": {
            "label": "Successfully exported",
            "color": "green"
        },
        "FAILED": {
            "label": "Export failed",
            "color": "red"
        }
    },
    "import": {
        "CREATED": {
            "label": "Upload created",
            "color": "grey"
        },
        "UPLOADING": {
            "label": "Waiting for user to upload files",
            "color": "yellow"
        },
        "UPLOADED": {
            "label": "Files uploaded",
            "color": "yellow"
        },
        "QUEUED": {
            "label": "Queued import for processing",
            "color": "yellow"
        },
        "PROCESSING": {
            "label": "Processing import",
            "color": "yellow"
        },
        "COMPLETE": {
            "label": "Finished import",
            "color": "green"
        },
        "FAILED": {
            "label": "Import failed for one or more uploads",
            "color": "red"
        }
    },
    "scene": {
        "NOTINGESTED": {
            "label": "Not scheduled for ingest",
            "color": "grey"
        },
        "TOBEINGESTED": {
            "label": "Queued for ingest",
            "color": "yellow"
        },
        "INGESTING": {
            "label": "Being ingested",
            "color": "yellow"
        },
        "INGESTED": {
            "label": "Successfully ingested",
            "color": "green"
        },
        "COG": {
            "label": "Cloud Optimized",
            "color": "blue"
        },
        "FAILED": {
            "label": "Ingest failed",
            "color": "red"
        }
    },
    "project": {
        "NOSCENES": {
            "label": "No scenes",
            "color": "grey"
        },
        "PARTIAL": {
            "label": "Not all scenes have been ingested",
            "color": "yellow"
        },
        "PENDINGREVIEW": {
            "label": "Scenes are pending review",
            "color": "yellow"
        },
        "CURRENT": {
            "label": "All scenes ingested",
            "color": "green"
        },
        "FAILED": {
            "label": "Ingest failed for some scenes",
            "color": "red"
        }
    }
};

export default (app) => {
    class StatusService {
        constructor() {
            'ngInject';
        }

        getStatusFields(entityType, status) {
            return _.get(statusMapping, [entityType, status]) || false;
        }
    }

    app.service('statusService', StatusService);
};
