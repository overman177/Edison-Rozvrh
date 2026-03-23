package cz.tal0052.edisonrozvrh.data.repository

import cz.tal0052.edisonrozvrh.data.parser.RoundcubeMailboxShellPage

interface RoundcubeMessageActionTransport {
    fun markMessage(
        shellPage: RoundcubeMailboxShellPage,
        uid: String,
        flag: String
    ): RoundcubeTransportResponse
}
