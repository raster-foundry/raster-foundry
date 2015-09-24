'use strict';

var React = require('react'),
    $ = require('jquery'),
    _ = require('underscore'),
    uploads = require('../../core/uploads');

var FileDescription = React.createBackboneClass({
    removeFileDescription: function() {
        this.props.removeFileDescription(this.props.fileDescription);
    },

    render: function() {
        return (
            <li className="list-group-item">
                {this.props.fileDescription.file.name}
                <i className='pull-right rf-icon-cancel text-danger' onClick={this.removeFileDescription}></i>
            </li>
        );
    }
});

var UploadModal = React.createBackboneClass({
    componentDidMount: function() {
        // see http://stackoverflow.com/questions/7110353/html5-dragleave-fired-when-hovering-a-child-element
        this.dragCounter = 0;

        var self = this;
        $('.import-modal').on('show.bs.modal', function() {
            self.clearFiles();
        });
    },

    componentWillUnmount: function() {
        $('.import-modal').off('show.bs.modal');
    },

    getInitialState: function() {
        return {
            dragActive: false,
            fileDescriptions: []
        };
    },

    clearFiles: function() {
        this.setState({fileDescriptions: []});
    },

    handleClickBrowse: function(e) {
        React.findDOMNode(this.refs.hiddenFileInput).click();
        e.preventDefault();
    },

    handleFileInputChange: function(e) {
        this.updateFiles(e.target.files);
    },

    uploadFiles: function(e) {
        e.preventDefault();
        try {
            uploads.uploadFiles(_.pluck(this.state.fileDescriptions, 'file'));
        } catch (excp) {
            if (excp instanceof uploads.S3UploadException) {
                // TODO Show something useful to the user here.
                console.error(excp);
            } else {
                throw excp;
            }
        }
    },

    updateFiles: function(files) {
        var oldFileDescriptions = _.forEach(this.state.fileDescriptions, function(fileDescription) {
                fileDescription.isNew = false;
            }),
            newFileDescriptions = _.map(files, function(file) {
                return {
                    file: file,
                    isNew: true
                };
            }),
            fileDescriptions = newFileDescriptions.concat(oldFileDescriptions);
        this.setState({'fileDescriptions': fileDescriptions});
    },

    removeFileDescription: function(fileDescription) {
        this.setState({'fileDescriptions': _.without(this.state.fileDescriptions, fileDescription)});
    },

    onDragEnter: function(e) {
        if (this.dragCounter === 0) {
            this.setState({dragActive: true});
        }
        this.dragCounter++;
        this.setState({dragActive: true});
        e.preventDefault();
    },

    onDragLeave: function(e) {
        this.dragCounter--;
        if (this.dragCounter === 0) {
            this.setState({dragActive: false});
        }
        e.preventDefault();
    },

    // Needed to prevent a bug in Chrome.
    onDragOver: function(e) {
        e.preventDefault();
    },

    onDrop: function(e) {
        e.preventDefault();
        this.updateFiles(e.dataTransfer.files);
        this.setState({dragActive: false});
        this.dragCounter = 0;
    },

    render: function() {
        var dragToAreaContent = <i className="rf-icon-upload-cloud-outline" />,
            self = this;
        if (this.state.fileDescriptions.length > 0) {
            dragToAreaContent = (
                <ul className="list-group text-left">
                    {_.map(this.state.fileDescriptions, function(fileDescription, i) {
                        return <FileDescription key={i}
                            fileDescription={fileDescription}
                            removeFileDescription={self.removeFileDescription}/>;
                    })}
                </ul>
            );
        }

        var dragToAreaClassName = this.state.dragActive ? 'drag-to-area active' : 'drag-to-area';

        return (
            <div className="modal import-modal fade in open" id="import-imagery-modal" tabIndex={-1} role="dialog" aria-labelledby="import-imagery-modal" aria-hidden="true">
                <div id="pane-1" className="pane animated fadeInDown active">
                    <div className="modal-dialog modal-lg">
                        <div className="modal-content">
                            <button type="button" className="close" data-dismiss="modal" aria-label="Close"><i className="rf-icon-cancel" /></button>
                            <div className="modal-body no-padding">
                                <div className="vertical-align-parent">
                                    <div className="vertical-align-child col-md-7 import-browse">
                                        <div className={dragToAreaClassName}
                                            onDragEnter={this.onDragEnter} onDragLeave={this.onDragLeave}
                                            onDragOver={this.onDragOver} onDrop={this.onDrop} >
                                            {dragToAreaContent}
                                            <h3 className="font-400">Drag &amp; Drop</h3>
                                            <h4 className="font-300">to store your imagery here, or <strong><a href="#"
                                                onClick={this.handleClickBrowse}>browse</a></strong>.</h4>
                                            {/* needed to put style inline because bootstrap sets the style in a way that can't be overridden */}
                                            <input type="file" id="files" ref="hiddenFileInput"
                                                style={{'display':'none'}}
                                                multiple onChange={this.handleFileInputChange} />
                                            <em>zip files or single image files accepted</em>
                                        </div>
                                    </div>
                                    <div className="vertical-align-child col-md-5 import-uri">
                                        <i className="rf-icon-link" />
                                        <h3 className="font-400">Upload with a URI</h3>
                                        <h4 className="font-300">Is your imagery hosted somewhere already? Enter the URI to import.</h4>
                                        <em>Examples: S3, DropBox, FTP</em>
                                        <form>
                                            <div className="form-group">
                                                <label>URI</label>
                                                <input className="form-control" type="url" />
                                            </div>
                                            <div className="form-action text-right">
                                                {/* <input type="submit" class="btn btn-primary" value="Continue"> */}
                                                {/* TODO move upload to the next screen -- this is just for testing */}
                                                <button className="btn btn-secondary" data-toggle-pane data-pane-target="#pane-2"
                                                    onClick={this.uploadFiles}>Continue</button>
                                            </div>
                                        </form>
                                    </div>
                                </div>
                            </div> {/* /.modal-body */}
                        </div> {/* /.modal-content */}
                    </div> {/* /.modal-dialog */}
                </div> {/* /#pane-1 */}
                <div id="pane-2" className="pane animated fadeInDown">
                    <div className="modal-dialog">
                        <div className="modal-content">
                            <button type="button" className="close" data-dismiss="modal" aria-label="Close"><span aria-hidden="true">×</span></button>
                            <div className="modal-body">
                                <form>
                                    <div className="form-group">
                                        <label>URI</label>
                                        <input className="form-control" type="url" />
                                    </div>
                                    <div className="form-group">
                                        <label>Description</label>
                                        <textarea className="form-control" rows={4} />
                                    </div>
                                    <div className="form-group">
                                        <label>Tags</label>
                                        <input className="form-control" type="url" />
                                    </div>
                                    <div className="row">
                                        <div className="col-md-6">
                                            <div className="form-group">
                                                <label>Capture Start Date</label>
                                                <input className="form-control" type="date" />
                                            </div>
                                        </div>
                                        <div className="col-md-6">
                                            <div className="form-group">
                                                <label>Capture End Date</label>
                                                <input className="form-control" type="date" />
                                            </div>
                                        </div>
                                    </div>
                                    <div className="row">
                                        <div className="col-md-6">
                                            <div className="form-group">
                                                <label>Area</label>
                                                <div className="select-group">
                                                    <input className="form-control" type="number" />
                                                    <select className="form-control">
                                                        <option>sq. mi</option>
                                                        <option>sq. km</option>
                                                    </select>
                                                </div>
                                            </div>
                                        </div>
                                        <div className="col-md-6">
                                            <div className="form-group">
                                                <label>Source Data Projection</label>
                                                <select className="form-control">
                                                </select>
                                            </div>
                                        </div>
                                    </div>
                                    <div className="row">
                                        <div className="col-md-6">
                                            <div className="form-group">
                                                <label>Total Images</label>
                                                <input className="form-control" type="number" readOnly />
                                            </div>
                                        </div>
                                    </div>
                                    <ul className="list-group">
                                        <li className="list-group-item">file_name.tiff <a href="#" className="pull-right"><i className="rf-icon-cancel text-danger" /></a></li>
                                        <li className="list-group-item">file_name_2.tiff <a href="#" className="pull-right"><i className="rf-icon-cancel text-danger" /></a></li>
                                        <li className="list-group-item">file_name_3.tiff <a href="#" className="pull-right"><i className="rf-icon-cancel text-danger" /></a></li>
                                    </ul>
                                    <div className="form-action">
                                        {/* <input type="submit" class="btn btn-secondary pull-right" value="Submit"> */}
                                        <button className="btn btn-secondary pull-right" data-toggle-pane data-pane-target="#pane-3">Submit</button>
                                        <button className="btn btn-link text-muted">Cancel</button>
                                    </div>
                                </form>
                            </div> {/* /.modal-body */}
                        </div> {/* /.modal-content */}
                    </div> {/* /.modal-dialog */}
                </div> {/* /#pane-2 */}
                <div id="pane-3" className="pane animated fadeInDown">
                    <div className="modal-dialog">
                        <div className="modal-content">
                            <button type="button" className="close" data-dismiss="modal" aria-label="Close"><span aria-hidden="true">×</span></button>
                            <div className="modal-body">
                                <form>
                                    <div className="row">
                                        <div className="col-md-8">
                                            <div className="form-group">
                                                <label>Source SRS</label>
                                                <input className="form-control" type="text" defaultValue="UTM Zone 18" readOnly />
                                            </div>
                                        </div>
                                        <div className="col-md-4">
                                            <label>&nbsp;</label>
                                            <button className="btn btn-default-outline btn-block" value="Change SRS">Change SRS</button>
                                        </div>
                                    </div>
                                    <div className="form-group">
                                        <label>Tile SRS</label>
                                        <select className="form-control">
                                            <option value>Web Mercator</option>
                                            <option value>UTM</option>
                                            <option value>WGS84 (EPSG: 4325)</option>
                                            <option value>EPSG/ESRI offline database</option>
                                        </select>
                                    </div>
                                    <hr />
                                    <h4>Mosaic Options</h4>
                                    <div className="form-group">
                                        <label>Tile Format</label>
                                        <select className="form-control">
                                            <option>JPEG</option>
                                            <option>Overlay PNG + optimisation (8 bit palette with alpha transparency)</option>
                                            <option>Overlay PNG format (32 bit RGBA with alpha transparency)</option>
                                            <option>Base map JPEG format (without transparency)</option>
                                            <option>Base map PNG format + optimisation (8 bit palette with alpha transparency)</option>
                                            <option>Base map PNG format (24 bit RGB without transparency)</option>
                                        </select>
                                    </div>
                                    <div className="form-group">
                                        <label>Resampling</label>
                                        <select className="form-control">
                                            <option value>Bilinear</option>
                                            <option value>Cubic</option>
                                            <option value>Cubic B-Spline</option>
                                            <option value>Average</option>
                                            <option value>Mode</option>
                                            <option value>Nearest Neighbor</option>
                                        </select>
                                    </div>
                                    <div className="form-group">
                                        <label>Transparency Settings</label><br />
                                        <div className="radio">
                                            <input name="transparency-setting" type="radio" defaultChecked />
                                            <label>Default</label>
                                        </div>
                                        <br />
                                        <div className="radio">
                                            <input name="transparency-setting" type="radio" />
                                            <label>Color to Alpha</label>
                                        </div>
                                        <input type="color" />
                                    </div>
                                    <div className="form-group">
                                        <label>Tiling Scheme</label><br />
                                        <div className="radio">
                                            <input name="tiling-scheme" type="radio" defaultChecked />
                                            <label>OGC WMTS / OpenStreetMap / Google XYZ (top-left origin)</label>
                                        </div>
                                        <br />
                                        <div className="radio">
                                            <input name="tiling-scheme" type="radio" />
                                            <label>OSGEO TMS (bottom-left origin)</label>
                                        </div>
                                    </div>
                                    <hr />
                                    <div className="form-group">
                                        <label>Image Upload Progress</label>
                                        <div className="progress">
                                            <div className="progress-bar" role="progressbar" aria-valuenow={60} aria-valuemin={0} aria-valuemax={100} style={{width: '60%'}}>
                                                <span className="sr-only">60% Complete</span>
                                            </div>
                                        </div>
                                        <em>Mosaic won't begin until upload has completed</em>
                                    </div>
                                    <div className="form-action">
                                        <input type="submit" className="btn btn-secondary pull-right" defaultValue="Submit" />
                                        <button className="btn btn-link text-muted">Cancel</button>
                                    </div>
                                </form>
                            </div> {/* /.modal-body */}
                        </div> {/* /.modal-content */}
                    </div> {/* /.modal-dialog */}
                </div> {/* /#pane-3 */}
            </div>
        );
    }
});

module.exports = UploadModal;
