import React from "react";
import EnableToggle from "../widgets/enable-toggle";
import { RunState } from "../../etc/enums";
import StartStopButton from "../widgets/start-stop-button";

import { Link } from "react-router";
import { urls } from "../../etc/urls";

import { Schedule } from "../../etc/enums";


class RepositoryRow extends React.Component {

    render() {
        const { repository, recordStatus, runState: { runState } } = this.props;

        const editLink = repository.enabled
            ? (<button className="btn btn-default" disabled={true}>
                <span className="glyphicon glyphicon-edit" />
            </button> )
            : (<Link to={urls.editRepository(repository.id)} className="btn btn-default">
                <span className="glyphicon glyphicon-pencil" />
            </Link>);

        const statusLink = (<Link to={urls.repositoryStatus(repository.id)} className="btn btn-default">
            <span className="glyphicon glyphicon-zoom-in" />
        </Link>);

        return (
            <tr>
                <td>{repository.id}</td>
                <td>{repository.name}</td>
                <td>{recordStatus.pending || 0}</td>
                <td>{recordStatus.processing || 0}</td>
                <td>{recordStatus.failure || 0}</td>
                <td>{recordStatus.processed || 0}</td>
                <td>{recordStatus["in preproces"] || 0}</td>
                <td>{repository.dateStamp || "- nog niet geharvest -"}</td>
                <td>{Schedule[repository.schedule].label}</td>
                <td>
                    <EnableToggle enabled={repository.enabled}
                                  toggleEnabled={runState === RunState.WAITING}
                                  onEnableClick={() => this.props.onEnableRepository(repository.id)}
                                  onDisableClick={() => this.props.onDisableRepository(repository.id)}/>
                </td>
                <td>
                    {statusLink}
                    {editLink}
                    <a className="btn btn-default"
                       href={`/record-status/errors/${repository.id}/${repository.name}.xlsx`}>
                        <img style={{height: "16px"}}
                            src="https://upload.wikimedia.org/wikipedia/commons/8/86/Microsoft_Excel_2013_logo.svg" />
                    </a>
                    <StartStopButton runState={runState || RunState.WAITING}
                                     disabled={!repository.enabled}
                                     onStopClick={() => this.props.onInterruptHarvest(repository.id)}
                                     onStartClick={() => this.props.onStartHarvest(repository.id)}/>
                </td>
            </tr>
        );
    }
}

export default RepositoryRow;