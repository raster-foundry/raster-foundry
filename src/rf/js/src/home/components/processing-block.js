'use strict';

var _ = require('underscore'),
    React = require('react');

var LayerStatusComponent = React.createBackboneClass({
    render: function() {
        var checkClass = 'rf-icon-check',
            spinnerClass = 'rf-icon-loader animate-spin',
            failedClass = 'rf-icon-attention rf-failed text-danger',
            preValidatedClass = spinnerClass,
            processingClass = spinnerClass,
            actionLink = (<a href="#" className="text-danger">Cancel</a>),
            preValidatedErrorsExist = false,
            layerError = false,
            layerErrorComponent = (
                <li>
                    {this.getModel().get('error') ? this.getModel().get('error') : 'An unknown error occured.'}
                    <i className="rf-icon-attention"></i>
                </li>
            ),
            preValidatedLabel = this.getModel().hasCopiedImages() ?
                'Transferring Images' : 'Uploading Images';

        if (this.getModel().isProcessing() ||
            this.getModel().isCompleted()) {
            preValidatedClass = checkClass;
        } else if (this.getModel().isFailed()) {
            preValidatedErrorsExist = _.some(this.getModel().get('images'), function(image) {
                return image.error && image.error !== '';
            });
            preValidatedClass = preValidatedErrorsExist ? failedClass : checkClass;
        }

        if (this.getModel().isCompleted()) {
            processingClass = checkClass;
        } else if (this.getModel().isFailed()) {
            processingClass = failedClass;
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
                            {preValidatedLabel} <i className={preValidatedClass} />
                            <ul className="notice">
                                {_.map(this.getModel().get('images'), function(image) {
                                    if (image.error && image.error !== '') {
                                        return (
                                            <li key={image.s3_uuid}>
                                                <strong>{image.file_name}</strong> {image.error}
                                                <i className="rf-icon-attention"></i>
                                            </li>
                                        );
                                    }
                                })}
                            </ul>
                        </li>
                        <li>Processing Tiles <i className={processingClass} />
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
