package com.dobbinsoft.fw.launcher.controller;

import com.dobbinsoft.fw.launcher.exception.LauncherExceptionDefinition;
import com.dobbinsoft.fw.launcher.manager.ApiDocumentModel;
import com.dobbinsoft.fw.launcher.manager.ClusterApiManager;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * Description:
 * User: rize
 * Date: 2018-09-13
 * Time: 上午9:31
 */
@Controller
@RequestMapping("/info")
public class DocumentController implements InitializingBean {

    @Autowired
    private ClusterApiManager clusterApiManager;

    private List exceptionDefinitionList;

    @RequestMapping("/")
    public ModelAndView index() {
        ApiDocumentModel.Group group = clusterApiManager.generateDocumentModel().getGroups().get(0);
        ModelAndView mv = new ModelAndView("redirect:/info/group/" + group.getName());
        return mv;
    }

    @RequestMapping("/api")
    public ModelAndView indexApi() {
        ApiDocumentModel.Group group = clusterApiManager.generateDocumentModel().getGroups().get(0);
        ModelAndView mv = new ModelAndView("redirect:/info/group/" + group.getName());
        return mv;
    }

    @RequestMapping("/group/{gp}")
    public ModelAndView group(@PathVariable("gp") String group) {
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.setViewName("document");
        modelAndView.addObject("model", clusterApiManager.generateGroupModel(group));
        modelAndView.addObject("groupNames", clusterApiManager.generateDocumentModel().getGroups());
        return modelAndView;
    }


    @RequestMapping("/api/{gp}/{mt}")
    public ModelAndView api(@PathVariable("gp") String gp, @PathVariable("mt") String mt) {
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.setViewName("method");
        modelAndView.addObject("methods", clusterApiManager.methods(gp));
        modelAndView.addObject("model", clusterApiManager.generateMethodModel(gp, mt));
        modelAndView.addObject("gp", gp);
        modelAndView.addObject("exceptionList", exceptionDefinitionList);
        return modelAndView;
    }

    @RequestMapping("/open")
    public ModelAndView openIndex() {
        ApiDocumentModel.Group group = clusterApiManager.generateOpenDocumentModel().getGroups().get(0);
        ModelAndView mv = new ModelAndView("redirect:/open/info/group/" + group.getName());
        return mv;
    }

    @RequestMapping("/open/group/{gp}")
    public ModelAndView openGroup(@PathVariable("gp") String group) {
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.setViewName("open_document");
        modelAndView.addObject("model", clusterApiManager.generateOpenGroupModel(group));
        modelAndView.addObject("groupNames", clusterApiManager.generateOpenDocumentModel().getGroups());
        return modelAndView;
    }

    @RequestMapping("/open/api/{gp}/{mt}")
    public ModelAndView openApi(@PathVariable("gp") String gp, @PathVariable("mt") String mt) {
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.setViewName("open_method");
        modelAndView.addObject("methods", clusterApiManager.methods(gp));
        modelAndView.addObject("model", clusterApiManager.generateOpenMethodModel(gp, mt));
        modelAndView.addObject("gp", gp);
        modelAndView.addObject("exceptionList", exceptionDefinitionList);
        return modelAndView;
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        exceptionDefinitionList = new LinkedList<>();
        Field[] fields = LauncherExceptionDefinition.class.getFields();
        for (Field field : fields) {
            exceptionDefinitionList.add(field.get(LauncherExceptionDefinition.class));
        }
    }
}
