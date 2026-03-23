package cz.tal0052.edisonrozvrh.data.repository

import cz.tal0052.edisonrozvrh.data.parser.RoundcubeMailboxShellPage

interface RoundcubeRemoteListTransport {
    fun fetchRemoteList(
        shellPage: RoundcubeMailboxShellPage,
        page: Int = shellPage.currentPage,
        includeRefresh: Boolean = false
    ): RoundcubeTransportResponse
}
