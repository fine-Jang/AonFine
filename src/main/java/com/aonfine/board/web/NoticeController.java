package com.aonfine.board.web;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.aonfine.auth.service.UserVO;
import com.aonfine.auth.web.AuthController;
import com.aonfine.board.service.BoardSearchVO;
import com.aonfine.board.service.BoardService;
import com.aonfine.board.service.BoardVO;

@Controller
public class NoticeController {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoticeController.class);
    private static final String BOARD_TYPE_NOTICE = "NOTICE";

    @Resource(name = "boardService")
    private BoardService boardService;

    @RequestMapping("/notice/list.do")
    public String list(@ModelAttribute("searchVO") BoardSearchVO searchVO, Model model) {
        searchVO.setBoardType(BOARD_TYPE_NOTICE);

        PaginationInfo paginationInfo = new PaginationInfo();
        paginationInfo.setCurrentPageNo(searchVO.getPageIndex());
        paginationInfo.setRecordCountPerPage(searchVO.getPageUnit());
        paginationInfo.setPageSize(10);

        searchVO.setFirstIndex(paginationInfo.getFirstRecordIndex());
        searchVO.setRecordCountPerPage(paginationInfo.getRecordCountPerPage());

        int totalCount = boardService.selectBoardListTotCnt(searchVO);
        paginationInfo.setTotalRecordCount(totalCount);

        LOGGER.info("Notice list request pageIndex={}, keyword={}, totalCount={}", searchVO.getPageIndex(), searchVO.getSearchKeyword(), totalCount);
        model.addAttribute("noticeList", boardService.selectBoardList(searchVO));
        model.addAttribute("paginationInfo", paginationInfo);
        return "notice/list";
    }

    @RequestMapping("/notice/detail.do")
    public String detail(@RequestParam("boardId") Integer boardId, Model model) {
        boardService.increaseViewCount(boardId);
        model.addAttribute("notice", boardService.selectBoard(boardId));
        LOGGER.info("Notice detail request boardId={}", boardId);
        return "notice/detail";
    }

    @RequestMapping("/notice/form.do")
    public String form(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/login.do?returnUrl=/notice/form.do";
        }
        BoardVO notice = new BoardVO();
        notice.setBoardType(BOARD_TYPE_NOTICE);
        model.addAttribute("notice", notice);
        model.addAttribute("mode", "insert");
        return "notice/form";
    }

    @RequestMapping("/notice/edit.do")
    public String edit(@RequestParam("boardId") Integer boardId, HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/notice/detail.do?boardId=" + boardId;
        }
        model.addAttribute("notice", boardService.selectBoard(boardId));
        model.addAttribute("mode", "update");
        return "notice/form";
    }

    @RequestMapping("/notice/insert.do")
    public String insert(@ModelAttribute BoardVO notice,
            HttpSession session,
            RedirectAttributes redirectAttributes,
            Model model) {
        UserVO loginUser = getLoginUser(session);
        if (!isAdmin(loginUser)) {
            return "redirect:/login.do?returnUrl=/notice/form.do";
        }
        try {
            notice.setBoardType(BOARD_TYPE_NOTICE);
            notice.setWriterId(loginUser.getUserId());
            notice.setWriterName(loginUser.getUserName());
            boardService.insertBoard(notice);
            LOGGER.info("Notice inserted adminId={}, title={}", loginUser.getUserId(), notice.getTitle());
        } catch (IllegalArgumentException e) {
            model.addAttribute("message", e.getMessage());
            model.addAttribute("notice", notice);
            model.addAttribute("mode", "insert");
            return "notice/form";
        }
        redirectAttributes.addFlashAttribute("message", "공지사항이 등록되었습니다.");
        return "redirect:/notice/list.do";
    }

    @RequestMapping("/notice/update.do")
    public String update(@ModelAttribute BoardVO notice,
            HttpSession session,
            RedirectAttributes redirectAttributes,
            Model model) {
        UserVO loginUser = getLoginUser(session);
        if (!isAdmin(loginUser)) {
            return "redirect:/notice/detail.do?boardId=" + notice.getBoardId();
        }
        try {
            notice.setBoardType(BOARD_TYPE_NOTICE);
            boardService.updateBoard(notice);
            LOGGER.info("Notice updated adminId={}, boardId={}, title={}", loginUser.getUserId(), notice.getBoardId(), notice.getTitle());
        } catch (IllegalArgumentException e) {
            model.addAttribute("message", e.getMessage());
            model.addAttribute("notice", notice);
            model.addAttribute("mode", "update");
            return "notice/form";
        }
        redirectAttributes.addFlashAttribute("message", "공지사항이 수정되었습니다.");
        return "redirect:/notice/detail.do?boardId=" + notice.getBoardId();
    }

    @RequestMapping("/notice/delete.do")
    public String delete(@RequestParam("boardId") Integer boardId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        UserVO loginUser = getLoginUser(session);
        if (!isAdmin(loginUser)) {
            return "redirect:/notice/detail.do?boardId=" + boardId;
        }
        boardService.deleteBoard(boardId);
        LOGGER.info("Notice deleted adminId={}, boardId={}", loginUser.getUserId(), boardId);
        redirectAttributes.addFlashAttribute("message", "공지사항이 삭제되었습니다.");
        return "redirect:/notice/list.do";
    }

    private boolean isAdmin(HttpSession session) {
        return isAdmin(getLoginUser(session));
    }

    private boolean isAdmin(UserVO loginUser) {
        return loginUser != null && loginUser.isAdmin();
    }

    private UserVO getLoginUser(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object loginUser = session.getAttribute(AuthController.LOGIN_SESSION_KEY);
        return loginUser instanceof UserVO ? (UserVO) loginUser : null;
    }
}