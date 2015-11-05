'use strict';

var _ = require('underscore'),
    React = require('react'),
    $ = require('jquery');

var LayerStatusComponent = React.createBackboneClass({
    successClass: 'rf-icon-check',
    pendingClass: 'rf-icon-loader',
    workingClass: 'rf-icon-loader animate-spin',
    failedClass: 'rf-icon-attention rf-failed text-danger',

    render: function() {
        var layer = this.getModel(),
            uploadClass = this.failedClass,
            copyClass = this.workingClass,
            completeClass = this.pendingClass,
            actionLinks = (<a href="javascript:;" onClick={this.deleteLayer} className="text-danger">Cancel</a>),
            copyErrorsExist = false,
            layerError = false,
            layerErrorComponent = (
                <li>
                    {layer.getErrorByName('failed') ? layer.getErrorByName('failed') : 'Processing failed.'}
                    <i className="rf-icon-attention"></i>
                </li>
            ),
            validateClass = this.updateStatusClass('validate'),
            thumbnailClass = this.updateStatusClass('thumbnail'),
            createWorkerClass = this.updateStatusClass('create_cluster'),
            chunkClass = this.updateStatusClass('chunk'),
            mosaicClass = this.updateStatusClass('mosaic');

        if (layer.isUploaded()) {
            uploadClass = this.successClass;
        } else if (layer.isUploading()) {
            uploadClass = this.workingClass;
        }

        if (layer.isCopied()) {
            copyClass = this.successClass;
        } else if (layer.isFailed()) {
            copyErrorsExist = _.some(layer.get('images'), function(image) {
                return !_.isEmpty(image.status_transfer_error);
            });
            copyClass = copyErrorsExist ? this.failedClass : this.successClass;
        }

        if (layer.isCompleted()) {
            completeClass = this.successClass;
        } else if (layer.isFailed()) {
            completeClass = this.failedClass;
            layerError = true;
        }

        if (layer.isDoneWorking()) {
            actionLinks = (<a onClick={this.dismiss}>Dismiss</a>);
            if (layer.retryPossible()) {
                actionLinks = (
                    <span>
                        {actionLinks}, <a onClick={this.retry}>Retry</a>
                    </span>
                );
            }
        }

        var uploadError = layer.getUploadError(),
            uploadErrorComponent = (
                <ul className="notice">
                    <li>
                        {uploadError}
                        <i className="rf-icon-attention"></i>
                    </li>
                </ul>
            ),
            uploadComponent = (
                <li>
                    Uploading Images <i className={uploadClass} />
                    {uploadError ? uploadErrorComponent : ''}
                </li>
            ),
            copyComponent = (
                <li>
                    Copying Images <i className={copyClass} />
                    <ul className="notice">
                        {_.map(layer.get('images'), function(image) {
                            if (image.status_transfer_error && image.status_transfer_error !== '') {
                                return (
                                    <li key={image.s3_uuid}>
                                        <strong>{image.file_name}</strong> {image.status_transfer_error}
                                        <i className="rf-icon-attention"></i>
                                    </li>
                                );
                            }
                        })}
                    </ul>
                </li>
            );

        return (
            <div className="list-group-item">
                <div className="list-group-content">
                    <h5>{layer.get('name')}</h5>
                    <ol>
                        {layer.hasUploadImages() ? uploadComponent : ''}
                        {layer.hasCopyImages() ? copyComponent : ''}
                        <li>
                            Validating Images <i className={validateClass} />
                            <ul className="notice">
                                {_.map(layer.get('images'), function(image) {
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
                                {_.map(layer.get('images'), function(image) {
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
                                })(layer.getErrorByName('create_cluster'))}
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
                                })(layer.getErrorByName('chunk'))}
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
                                })(layer.getErrorByName('mosaic'))}
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
                    { actionLinks }
                </div>
            </div>
        );
    },

    deleteLayer: function(e) {
        e.preventDefault();
        if (window.confirm('Are you sure you would like to cancel processing this layer?')) {
            this.getModel().destroy();
        }
    },

    dismiss: function(e) {
        e.preventDefault();
        var layer = this.getModel();
        layer.dismiss();
        this.props.removeItem(layer);
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
    },

    retry: function(e) {
        e.preventDefault();
        this.getModel().retry();
    }
});

var ProcessingBlock = React.createBackboneClass({
    componentDidMount: function() {
        this.getCollection().on('add', function() {
            var $blockTitle = $('.processing-block .block-title');
            if ($blockTitle.hasClass('collapsed')) {
                $blockTitle.trigger('click');
            }
        });
    },

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
