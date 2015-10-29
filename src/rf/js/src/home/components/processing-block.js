'use strict';

var _ = require('underscore'),
    React = require('react');

var LayerStatusComponent = React.createBackboneClass({
    successClass: 'rf-icon-check',
    pendingClass: 'rf-icon-loader',
    workingClass: 'rf-icon-loader animate-spin',
    failedClass: 'rf-icon-attention rf-failed text-danger',

    render: function() {
        var uploadingClass = this.workingClass,
            validateClass = this.pendingClass,
            thumbnailClass = this.pendingClass,
            createWorkerClass = this.pendingClass,
            chunkClass = this.pendingClass,
            mosaicClass = this.pendingClass,
            completeClass = this.pendingClass,
            actionLink = (<a className="text-danger">Cancel</a>),
            uploadErrorsExist = false,
            layerError = false,
            layerErrorComponent = (
                <li>
                    {this.getModel().getErrorByName('failed') ? this.getModel().getErrorByName('failed') : 'Processing failed.'}
                    <i className="rf-icon-attention"></i>
                </li>
            ),
            uploadLabel = this.getModel().hasCopiedImages() ?
                'Transferring Images' : 'Uploading Images';

        if (this.getModel().isUploaded()) {
            uploadingClass = this.successClass;
        } else if (this.getModel().isFailed()) {
            uploadErrorsExist = _.some(this.getModel().get('images'), function(image) {
                return !_.isEmpty(image.status_upload_error);
            });
            uploadingClass = uploadErrorsExist ? this.failedClass : this.successClass;
        }

        validateClass = this.updateStatusClass('validate');
        thumbnailClass = this.updateStatusClass('thumbnail');
        createWorkerClass = this.updateStatusClass('create_cluster');
        chunkClass = this.updateStatusClass('chunk');
        mosaicClass = this.updateStatusClass('mosaic');

        if (this.getModel().isCompleted()) {
            completeClass = this.successClass;
        } else if (this.getModel().isFailed()) {
            completeClass = this.failedClass;
            layerError = true;
        }

        if (this.getModel().isDoneWorking()) {
            actionLink = (<a onClick={this.dismiss}>Dismiss</a>);
        }

        return (
            <div className="list-group-item">
                <div className="list-group-content">
                    <h5>{this.getModel().get('name')}</h5>
                    <ol>
                        <li>
                            {uploadLabel} <i className={uploadingClass} />
                            <ul className="notice">
                                {_.map(this.getModel().get('images'), function(image) {
                                    if (image.status_upload_error && image.status_upload_error !== '') {
                                        return (
                                            <li key={image.s3_uuid}>
                                                <strong>{image.file_name}</strong> {image.status_upload_error}
                                                <i className="rf-icon-attention"></i>
                                            </li>
                                        );
                                    }
                                })}
                            </ul>
                        </li>
                        <li>
                            Validating Images <i className={validateClass} />
                            <ul className="notice">
                                {_.map(this.getModel().get('images'), function(image) {
                                    if (image.status_validate_error && image.status_validate_error !== '') {
                                        return (
                                            <li key={image.s3_uuid}>
                                                <strong>{image.file_name}</strong> {image.status_validate_error}
                                                <i className="rf-icon-attention"></i>
                                            </li>
                                        );
                                    }
                                })}
                            </ul>
                        </li>
                        <li>
                            Creating Thumbnails <i className={thumbnailClass} />
                            <ul className="notice">
                                {_.map(this.getModel().get('images'), function(image) {
                                    if (image.error && image.error !== '') {
                                        return (
                                            <li key={image.s3_uuid}>
                                                <strong>{image.file_name}</strong> {image.status_thumbnail_error}
                                                <i className="rf-icon-attention"></i>
                                            </li>
                                        );
                                    }
                                })}
                            </ul>
                        </li>
                        <li>
                            Preparing Workers <i className={createWorkerClass} />
                            <ul className="notice">
                                {(function(error) {
                                    if (error) {
                                        return (
                                            <li>
                                                {error}
                                                <i className="rf-icon-attention"></i>
                                            </li>
                                        );
                                    }
                                })(this.getModel().getErrorByName('create_cluster'))}
                            </ul>
                        </li>
                        <li>
                            Chunking Tiles <i className={chunkClass} />
                            <ul className="notice">
                                {(function(error) {
                                    if (error) {
                                        return (
                                            <li>
                                                {error}
                                                <i className="rf-icon-attention"></i>
                                            </li>
                                        );
                                    }
                                })(this.getModel().getErrorByName('chunk'))}
                            </ul>
                        </li>
                        <li>
                            Merging Tiles <i className={mosaicClass} />
                            <ul className="notice">
                                {(function(error) {
                                    if (error) {
                                        return (
                                            <li>
                                                {error}
                                                <i className="rf-icon-attention"></i>
                                            </li>
                                        );
                                    }
                                })(this.getModel().getErrorByName('mosaic'))}
                            </ul>
                        </li>
                        <li>
                            Complete <i className={completeClass} />
                            <ul className="notice">
                                {layerError ? layerErrorComponent : null}
                            </ul>
                        </li>
                    </ol>
                </div>
                <div className="list-group-tool">
                    { actionLink }
                </div>
            </div>
        );
    },

    dismiss: function(e) {
        e.preventDefault();
        var model = this.getModel();
        model.dismiss();
        this.props.removeItem(model);
    },

    updateStatusClass: function(status) {
        var modelStatus = this.getModel().getStatusByName(status),
            className = this.pendingClass;

        if (modelStatus.started) {
            className = this.workingClass;
        }
        if (modelStatus.failed) {
            className = this.failedClass;
        } else if (modelStatus.finished) {
            className = this.successClass;
        }
        return className;
    }
});

var ProcessingBlock = React.createBackboneClass({
    render: function() {
        var self = this;
        if (this.getCollection().length === 0) {
            return null;
        }
        return (
            <div className="processing-block">
                <h5>
                    <a className="block-title collapsed" role="button" data-toggle="collapse" href="#processing-content" aria-expanded="false" aria-controls="processing-content">Processing Layers</a>
                </h5>
                <div className="collapse" id="processing-content">
                    <div className="list-group">
                        {this.getCollection().map(function(layer) {
                            return <LayerStatusComponent removeItem={self.removeItem} model={layer} key={layer.cid} />;
                        })}
                    </div>
                </div>
          </div>
        );
    },

    removeItem: function(model) {
        this.getCollection().remove(model);
    }
});

module.exports = ProcessingBlock;
