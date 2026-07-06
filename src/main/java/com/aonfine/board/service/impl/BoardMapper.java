package com.aonfine.board.service.impl;

import java.util.List;

import com.aonfine.board.service.BoardSearchVO;
import com.aonfine.board.service.BoardVO;

public interface BoardMapper {
    List<BoardVO> selectBoardList(BoardSearchVO searchVO);

    int selectBoardListTotCnt(BoardSearchVO searchVO);

    BoardVO selectBoard(Integer boardId);

    void increaseViewCount(Integer boardId);

    void insertBoard(BoardVO boardVO);

    void updateBoard(BoardVO boardVO);

    void deleteBoard(Integer boardId);
}