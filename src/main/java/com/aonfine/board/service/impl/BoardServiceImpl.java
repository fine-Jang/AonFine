package com.aonfine.board.service.impl;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.aonfine.board.service.BoardSearchVO;
import com.aonfine.board.service.BoardService;
import com.aonfine.board.service.BoardVO;

@Service("boardService")
public class BoardServiceImpl implements BoardService {

    @Resource(name = "boardMapper")
    private BoardMapper boardMapper;

    public List<BoardVO> selectBoardList(BoardSearchVO searchVO) {
        return boardMapper.selectBoardList(searchVO);
    }

    public int selectBoardListTotCnt(BoardSearchVO searchVO) {
        return boardMapper.selectBoardListTotCnt(searchVO);
    }

    public BoardVO selectBoard(Integer boardId) {
        return boardMapper.selectBoard(boardId);
    }

    public void increaseViewCount(Integer boardId) {
        boardMapper.increaseViewCount(boardId);
    }

    public void insertBoard(BoardVO boardVO) {
        validateBoard(boardVO);
        boardMapper.insertBoard(boardVO);
    }

    public void updateBoard(BoardVO boardVO) {
        validateBoard(boardVO);
        boardMapper.updateBoard(boardVO);
    }

    public void deleteBoard(Integer boardId) {
        boardMapper.deleteBoard(boardId);
    }

    private void validateBoard(BoardVO boardVO) {
        if (boardVO == null || !StringUtils.hasText(boardVO.getTitle())) {
            throw new IllegalArgumentException("제목을 입력하세요.");
        }
        if (!StringUtils.hasText(boardVO.getContent())) {
            throw new IllegalArgumentException("내용을 입력하세요.");
        }
    }
}