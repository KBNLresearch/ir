const ActionTypes = {
    REQUEST_REPOSITORIES: "REQUEST_REPOSITORIES",
    RECEIVE_REPOSITORIES: "RECEIVE_REPOSITORIES",
    RECEIVE_NEW_REPOSITORY_VALIDATION_RESULTS: "RECEIVE_NEW_REPOSITORY_VALIDATION_RESULTS",
    ON_SAVE_REPOSITORY: "ON_SAVE_REPOSITORY",

    RECEIVE_RECORD_STATUS: "RECEIVE_RECORD_STATUS",
    RECEIVE_ERROR_STATUS: "RECEIVE_ERROR_STATUS",
    RECEIVE_STATUS_CODES: "RECEIVE_STATUS_CODES",

    ON_STATUS_UPDATE: "ON_STATUS_UPDATE",
    ON_SOCKET_CLOSED: "ON_SOCKET_CLOSED",

    ON_FETCHER_RUNSTATE_CHANGE: "ON_FETCHER_RUNSTATE_CHANGE",
    RECEIVE_HARVESTER_RUNSTATE: "RECEIVE_HARVESTER_RUNSTATE",

    RECEIVE_CREDENTIALS: "RECEIVE_CREDENTIALS",

    FIND_RESULT_PENDING: "FIND_RESULT_PENDING",
    RECEIVE_FIND_RESULT: "RECEIVE_FIND_RESULT",
    CLEAR_FOUND_RECORDS: "CLEAR_FOUND_RECORDS",
    RECORD_PENDING: "RECORD_PENDING",
    RECEIVE_RECORD: "RECEIVE_RECORD"
};

export default ActionTypes;