package com.aonfine.board.service;

import java.util.List;

public interface BoardService {
    List<BoardVO> selectBoardList(BoardSearchVO searchVO);

    int selectBoardListTotCnt(BoardSearchVO searchVO);

    BoardVO selectBoard(Integer boardId);

    void increaseViewCount(Integer boardId);

    void insertBoard(BoardVO boardVO);

    void updateBoard(BoardVO boardVO);

    void deleteBoard(Integer boardId);
}